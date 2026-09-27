package com.orionisli.gh0stra1n.ipc;

interface IGh0stOverlayService {
    boolean isRootAlive();
    int getPartitionStatus(String partitionName);
    String getUpperDir(String partitionName);
    long getPartitionFreeSpace(String partitionName);
    boolean mountPartition(String partitionName);
    boolean unmountPartition(String partitionName);
    void syncStorage();
    boolean restartZygote();
    boolean executeRootCommand(String command);
}
