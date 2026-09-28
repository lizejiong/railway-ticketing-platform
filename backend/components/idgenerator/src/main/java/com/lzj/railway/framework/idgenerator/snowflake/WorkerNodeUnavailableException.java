package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * 当前 ID 域中没有可分配的工作节点编号时抛出。
 */
public class WorkerNodeUnavailableException extends IllegalStateException {

    public WorkerNodeUnavailableException() {
        super("no worker node id is available");
    }
}
