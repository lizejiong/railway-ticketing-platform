package com.lzj.railway.framework.idgenerator.snowflake;

/**
 * 使用部署配置中显式指定节点编号的策略。
 */
public final class FixedWorkerNodeAssigner implements WorkerNodeAssigner {

    private final WorkerNode workerNode;

    public FixedWorkerNodeAssigner(long nodeId) {
        this.workerNode = new WorkerNode(nodeId);
    }

    @Override
    public WorkerNode assign() {
        return workerNode;
    }
}
