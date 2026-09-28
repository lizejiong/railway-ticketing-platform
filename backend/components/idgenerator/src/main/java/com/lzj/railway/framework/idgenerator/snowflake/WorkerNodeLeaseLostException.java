package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * 当前实例失去工作节点编号租约时抛出。
 */
public class WorkerNodeLeaseLostException extends IllegalStateException {

    public WorkerNodeLeaseLostException() {
        super("worker node lease has been lost");
    }
}
