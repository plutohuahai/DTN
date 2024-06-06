/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

//import org.apache.xmlrpc.XmlRpcException;

import routing.util.RoutingInfo;
import smile.clustering.KMeans;
import util.Tuple;
import core.Application;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.DTNSim;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.SimScenario;

/**
 * K-means在DTN中的应用
 */
public class KropRouter extends ActiveRouter {

	private Map<DTNHost, Integer> encounter;
	private int successDelivered;
	private int label;
	private int k = 2;        //簇数量

	// Spray&Wait配置文件字段
	public static final String NROF_COPIES = "nrofCopies";// 配置文件字段，消息的份数
	public static final String BINARY_MODE = "binaryMode";// 配置文件字段，分发策略，true为一半一半地分，false为一份一份地分
	public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";

	protected int initialNrofCopies;
	protected boolean isBinary;

	public KropRouter(Settings s) {
		super(s);
		initPreds();

		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean(BINARY_MODE);
	}

	protected KropRouter(KropRouter r) {
		super(r);
		initPreds();
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
	}

	private void initPreds() {
		this.encounter = new HashMap<DTNHost, Integer>();
	}

	@Override
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, Integer.valueOf(initialNrofCopies));// 添加字段，每个消息需要一个字段保存份数copies
		addToMessages(msg, true);
		return true;
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);

		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			if (!encounter.containsKey(otherHost)) {
				encounter.put(otherHost, 1);
			} else {
				int encounter_Count = encounter.get(otherHost) + 1;
				encounter.put(otherHost, encounter_Count);
			}
		}
	}

	/**
	 * 得到与给定节点的相遇次数
	 */
	public int getCountFor(DTNHost host) {
		if (encounter.containsKey(host)) {
			return encounter.get(host);
		} else {
			return 0;
		}
	}

	/**
	 * 得到节点相遇历史记录
	 */
	public Map<DTNHost, Integer> getEncounterCord() {
		return this.encounter;
	}

	/**
	 * 得到与给定节点的距离
	 */
	public double getDistance(DTNHost host) {
		Coord otherlocation = host.getLocation();
		Coord thislocation = this.getHost().getLocation();
		double distance = thislocation.distance(otherlocation);
		return distance;
	}

	/**
	 * 得到该节点剩余缓存空间
	 */
	public int getReamainBuffer() {
		return super.getFreeBufferSize();
	}

	/**
	 * 得到该节点成功投递的消息数
	 */
	public int getSuccessDelivered() {
		return this.successDelivered;
	}

	/**
	 * 设置该节点成功投递的消息数
	 */
	public void setSuccessDelivered(int count) {
		this.successDelivered = count;
	}

	/**
	 * 得到该节点标记的类别
	 */
	public int getLabel() {
		return this.label;
	};

	/**
	 * 设置该节点标记的类别
	 */
	public void SetLabel(int label) {
		this.label = label;
	};

	// 假设A发送消息m给B，那么传输完毕后，A和B的缓存区都有m，都需要对m进行更新copies。值得注意的是，更新发送端和接收端都由A发起
	@Override
	protected void transferDone(Connection con) {// 更新发送端
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);

		if (msg == null) {
			return;
		}
		nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) {
			nrofCopies /= 2;
		} else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

		assert nrofCopies != null : "Not a SnW message: " + msg;

		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		} else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}

		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}

	// getMessagesWithCopiesLeft在getMessageCollection()的基础上，过滤掉那些copies小于1的消息
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + "nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}

		return list;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		if (exchangeDeliverableMessages() != null) {
			return;
		}

		tryOtherMessages();
	}

	private Tuple<Message, Connection> tryOtherMessages() {

		List<Tuple<Message, Connection>> messages = new ArrayList<>();// 发送队列
		List<DTNHost> hosts = SimScenario.hosts;

		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

		// 邻居节点小于2的情况
		int size = getConnections().size();
		if (size < 2) {
			tryMessagesToConnections(copiesLeft, getConnections());
			return null;
		}

		
		for (Message m : copiesLeft) {
			DTNHost destination = m.getTo();
			int count = 0;
			int totalEncounter = 0;// 最大相遇次数
			int totalSd = 0;// 最大投递数量
			
			//计算与最佳中心的比较数据
			for (DTNHost h : hosts) {
				if (h == destination)
					continue;
				KropRouter router = (KropRouter) h.getRouter();
				totalEncounter += router.getCountFor(destination);
				totalSd += router.deliveredMessages.size();
			}
			
			int connSize = getConnections().size();
			double[][] sampleObject = new double[connSize][4];
			for (int i = 0; i < connSize; i++) {
				Connection con = getConnections().get(i);
				KropRouter otherRouter = (KropRouter) con.getOtherNode(getHost()).getRouter();
				sampleObject[count][0] = otherRouter.getCountFor(destination);
				sampleObject[count][1] = otherRouter.getDistance(destination);
				sampleObject[count][2] = otherRouter.getReamainBuffer();
				sampleObject[count][3] = otherRouter.getSuccessDelivered();
			}
			
			double[][] dataT = transposeMatrix(sampleObject); // 转置矩阵
			normalizeMatrix(dataT); // 归一化矩阵
			
			KMeans kmeans = KMeans.fit(dataT, k);   // 训练数据
			int[] labels = kmeans.y;

			// 记录平均距离
			double[] sumD = new double[k];
			int[] sumC = new int[k];
			for (int i = 0; i < sampleObject.length; i++) {
				double number = Math.sqrt(Math.pow((sampleObject[i][0] - totalEncounter), 2)
						+ Math.pow((sampleObject[i][1] - 0), 2) 
						+ Math.pow((sampleObject[i][2] - 50000000), 2)
						+ Math.pow((sampleObject[i][3] - totalSd), 2));
				sumD[labels[i]] += number;
				sumC[labels[i]]++;
			}
			
			int minLabel = 0;
			double minAverageDistance = sumD[0] / sumC[0];
			
			for (int i = 1; i < sumD.length; i++) {
				double averageDistance = sumD[i] /sumC[0];
				if (averageDistance < minAverageDistance) {
					minLabel = i;
					minAverageDistance = averageDistance;
				}
			}

	 		for (int i = 0; i < connSize; i++) {
				if (labels[i] == minLabel) {
					messages.add(new Tuple<>(m, getConnections().get(i)));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		return tryMessagesForConnected(messages); // try to send messages
	}
	
	
	//矩阵转置
	private double[][] transposeMatrix(double[][] matrix) {
		double[][] transposed = new double[matrix[0].length][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				transposed[j][i] = matrix[i][j];
			}
		}
		return transposed;
	}
	
	//归一化矩阵
	private void normalizeMatrix(double[][] matrix) {
		for (int i = 0;i < matrix.length; i++) {
			Arrays.sort(matrix[i]);
			double minVal = matrix[i][0];
			double maxVal = matrix[i][matrix[i].length - 1];
			for (int j = 0; j < matrix[i].length; j++) {
				matrix[i][j] = (matrix[i][j] - minVal) / (maxVal - minVal);
			}
		}
	}
	

	
	@Override
	public MessageRouter replicate() {
		KropRouter r = new KropRouter(this);
		return r;
	}

}
