# DTN聚类算法项目

## 项目简介

<p align="center">
  本项目旨在研究和实现延迟容忍网络（Delay-Tolerant Networking, DTN）中的聚类算法。DTN是一种无连接的网络体系结构，适用于网络断断续续、高延迟或高度动态的环境，如移动网络和卫星通信。
</p>

## 目录结构

- [源代码](src)
  - [DecisionEngineRouter.java](src/DecisionEngineRouter.java)
  - [DistributedBubbleRap.java](src/DistributedBubbleRap.java)
  - [CommunityDetection.java](src/CommunityDetection.java)
  - [SimpleCommunityDetection.java](src/SimpleCommunityDetection.java)
  - [Centrality.java](src/Centrality.java)
  - [SWindowCentrality.java](src/SWindowCentrality.java)
  - [KropRouter.java]([src/KropRouter.java](https://github.com/plutohuahai/DTN/blob/main/KropRouter.java))

## 聚类算法

### 1. 分布式BubbleRap算法

<p align="center">
  分布式BubbleRap算法通过全局中心性节点向本地社区节点转发消息，以优化消息传递效率。
</p>

### 2. 简单社区检测算法

<p align="center">
  简单社区检测算法用于识别本地社区，并支持其他聚类算法的实现。
</p>

### 3. 窗口中心性算法

<p align="center">
  窗口中心性算法用于度量节点的中心性，并帮助判断最优节点转发策略。
</p>


