package org.top.core.log;

import lombok.extern.slf4j.Slf4j;
import org.top.core.AppendEntriesComponent;
import org.top.core.ClientNum;
import org.top.core.RaftServerData;
import org.top.core.ServerStateEnum;
import org.top.core.machine.KvStateMachineImpl;
import org.top.core.machine.OptionEnum;
import org.top.core.machine.StateMachine;
import org.top.models.LogEntry;
import org.top.models.PersistentStateModel;
import org.top.models.ServerStateModel;
import org.top.rpc.NodeGroup;
import org.top.rpc.entity.SubmitRequest;
import org.top.rpc.entity.SubmitResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @author lubeilin
 * @date 2020/11/19
 */
@Slf4j
public class OperationFacadeImpl implements OperationFacade {
    private AppendEntriesComponent appendEntriesComponent = new AppendEntriesComponent();
    private StateMachine stateMachine = new KvStateMachineImpl();
    private SubmitResponse result;
    private long index;
    private byte[] id;

    @Override
    public SubmitResponse submit(SubmitRequest msg) {
        try {
            if (msg.getKey() == null) {
                result = new SubmitResponse(SubmitResponse.ERROR, null, msg.getId(), "key不能为空".getBytes(StandardCharsets.UTF_8));
                return result;
            }
            if (RaftServerData.serverStateEnum == ServerStateEnum.LEADER && ClientNum.getNum() >= NodeGroup.getNodeGroup().majority() - 1) {
                LogEntry logEntry = new LogEntry();

                OptionEnum optionEnum = OptionEnum.getByCode(msg.getOption());
                switch (optionEnum) {
                    case GET:
                        if (RaftServerData.isUp()) {
                            result = new SubmitResponse(SubmitResponse.SUCCESS, null, msg.getId(), stateMachine.get(msg.getKey()));
                        } else {
                            result = new SubmitResponse(SubmitResponse.TURN, RaftServerData.leaderId, msg.getId(), null);
                        }
                        return result;
                    case DEL:
                        break;
                    case SET:
                        logEntry.setVal(msg.getVal());
                        break;
                    default:
                        result = new SubmitResponse(SubmitResponse.FAIL, null, msg.getId(), "类型错误".getBytes(StandardCharsets.UTF_8));
                        return result;
                }

                PersistentStateModel model = PersistentStateModel.getModel();
                logEntry.setKey(msg.getKey());
                logEntry.setId(msg.getId());
                logEntry.setOption(msg.getOption());
                model.pushLast(logEntry);
                index = logEntry.getIndex();
                id = msg.getId();
                appendEntriesComponent.broadcastAppendEntries();
                return null;
            } else {
                result = new SubmitResponse(SubmitResponse.TURN, RaftServerData.leaderId, msg.getId(), null);
                return result;
            }
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            result = new SubmitResponse(SubmitResponse.ERROR, null, msg.getId(), e.getMessage().getBytes(StandardCharsets.UTF_8));
            return result;
        }
    }

    @Override
    public void await() {
        if (result == null) {
            try {
                if (new LogIndexSemaphore(index).await(20, TimeUnit.SECONDS)) {
                    result = new SubmitResponse(SubmitResponse.SUCCESS, null, id, null);
                    return;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ServerStateModel serverState = RaftServerData.serverState;

            if (serverState.getCommitIndex() >= index &&
                    RaftServerData.serverStateEnum == ServerStateEnum.LEADER
                    && ClientNum.getNum() >= NodeGroup.getNodeGroup().majority() - 1) {
                result = new SubmitResponse(SubmitResponse.SUCCESS, null, id, null);
            } else if (RaftServerData.serverStateEnum == ServerStateEnum.LEADER
                    && ClientNum.getNum() >= NodeGroup.getNodeGroup().majority() - 1) {
                result = new SubmitResponse(SubmitResponse.ERROR, null, id, "等待超时".getBytes(StandardCharsets.UTF_8));
            } else {
                result = new SubmitResponse(SubmitResponse.ERROR, null, id, null);
            }
        }

    }

    @Override
    public SubmitResponse result() {
        return result;
    }
}
