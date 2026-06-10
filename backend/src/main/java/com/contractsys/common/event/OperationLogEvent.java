package com.contractsys.common.event;

import com.contractsys.user.SysUser;

public record OperationLogEvent(
        SysUser operator,
        String module,
        String action,
        String targetType,
        Long targetId,
        String content
) {
}
