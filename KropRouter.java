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

import org.apache.xmlrpc.XmlRpcException;

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
 * K-means��DTN�е�Ӧ��
 */
public class KropRouter extends ActiveRouter {

	private Map<DTNHost, Integer> encounter = null;
	private int successDelivered;
	private int label;

	// Spray&Wait�����ļ��ֶ�
	public static final String NROF_COPIES = "nrofCopies";// �����ļ��ֶΣ���Ϣ�ķ���
	public static final String BINARY_MODE = "binaryMode";// �����ļ��ֶΣ��ַ����ԣ�trueΪһ��һ��ط֣�falseΪһ��һ�ݵط�
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
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));// ����ֶΣ�ÿ����Ϣ��Ҫһ���ֶα������copies
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
	 * �õ�������ڵ����������
	 */
	public int getCountFor(DTNHost host) {
		if (encounter.containsKey(host)) {
			return encounter.get(host);
		} else {
			return 0;
		}
	}

	/**
	 * �õ��ڵ�������ʷ��¼
	 */
	public Map<DTNHost, Integer> getEncounterCord() {
		return this.encounter;
	}

	/**
	 * �õ�������ڵ�ľ���
	 */
	public double getDistance(DTNHost host) {
		Coord otherlocation = host.getLocation();
		Coord thislocation = this.getHost().getLocation();
		double distance = thislocation.distance(otherlocation);
		return distance;
	}

	/**
	 * �õ��ý��ʣ�໺��ռ�
	 */
	public int getReamainBuffer() {
		return super.getFreeBufferSize();
	}

	/**
	 * �õ��ý��ɹ�Ͷ�ݵ���Ϣ��
	 */
	public int getSuccessDelivered() {
		return this.successDelivered;
	}

	/**
	 * ���øý��ɹ�Ͷ�ݵ���Ϣ��
	 */
	public void setSuccessDelivered(int count) {
		this.successDelivered = count;
	}

	/**
	 * �õ��ý���ǵ����
	 */
	public int getLabel() {
		return this.label;
	};

	/**
	 * �õ��ý���ǵ����
	 */
	public void SetLabel(int label) {
		this.label = label;
	};

	// ����A������Ϣm��B����ô������Ϻ�A��B�Ļ���������m������Ҫ��m����copies��ֵ��ע����ǣ����·��Ͷ˺ͽ��ն˶���A����
	@Override
	protected void transferDone(Connection con) {// ���·��Ͷ�
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

	// getMessagesWithCopiesLeft��getMessageCollection()�Ļ����ϣ����˵���ЩcopiesС��1����Ϣ
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

		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();// ���Ͷ���
		List<DTNHost> hosts = SimScenario.hosts;

		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

		// �ھӽڵ�С��2�����
		int size = getConnections().size();
		if (size < 2) {
			tryMessagesToConnections(copiesLeft, getConnections());
			return null;
		}

		for (Message m : copiesLeft) {
			DTNHost destination = m.getTo();
			int count = 0;
			// ��������ıȽ�
			int totalEncounter = 0;// �����������
			int totalSd = 0;// ���Ͷ������
			for (DTNHost h : hosts) {
				if (h == destination)
					continue;
				KropRouter router = (KropRouter) h.getRouter();
				totalEncounter += router.getCountFor(destination);
				totalSd += router.deliveredMessages.size();
			}
			double[][] sampleObject = new double[getConnections().size()][4];
			for (Connection con : getConnections()) {
				KropRouter otherrouter = (KropRouter) con.getOtherNode(getHost()).getRouter();
				// ����������python
				sampleObject[count][0] = otherrouter.getCountFor(destination);
				sampleObject[count][1] = otherrouter.getDistance(destination);
				sampleObject[count][2] = otherrouter.getReamainBuffer();
				sampleObject[count][3] = otherrouter.getSuccessDelivered();
				count++;
			}
			double[][] dataT = new double[4][getConnections().size()];// ת�þ���
			double[][] data_train = new double[getConnections().size()][4];// ��һ����ľ���

			for (int i = 0; i < sampleObject.length; i++) {
				for (int j = 0; j < sampleObject[i].length; j++) {
					dataT[j][i] = sampleObject[i][j];
				}
			}
			for (int i = 0; i < dataT.length; i++) {
				Arrays.sort(dataT[i]);
			}
			for (int i = 0; i < data_train.length; i++) {
				for (int j = 0; j < data_train[i].length; j++) {
					// �����С��һ��
					data_train[i][j] = (sampleObject[i][j] - dataT[j][0]) / (dataT[j][dataT[j].length - 1] - dataT[j][0]);
				}
			}
			// ѵ������
			KMeans kmeans = KMeans.fit(data_train, 2);
			int[] labels = kmeans.y;

			// ��¼ƽ������
			double[] sumD = new double[kmeans.k];
			int[] sumC = new int[kmeans.k];
			for (int i = 0; i < sampleObject.length; i++) {
				double number = Math.sqrt(Math.pow((sampleObject[i][0] - totalEncounter), 2)
						+ Math.pow((sampleObject[i][1] - 0), 2) + Math.pow((sampleObject[i][2] - 50000000), 2)
						+ Math.pow((sampleObject[i][3] - totalSd), 2));
				sumD[labels[i]] += number;
				sumC[labels[i]]++;
			}
			int minLabel;
			if ((sumD[0] / sumC[0]) < (sumD[1] / sumC[1])) {
				minLabel = 0;
			} else {
				minLabel = 1;
			}

			List<Connection> connections = getConnections();
			for (int i = 0; i < connections.size(); i++) {
				Connection con = connections.get(i);
				if (labels[i] == minLabel) {
					messages.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		return tryMessagesForConnected(messages); // try to send messages
	}

	@Override
	public MessageRouter replicate() {
		KropRouter r = new KropRouter(this);
		return r;
	}

}
