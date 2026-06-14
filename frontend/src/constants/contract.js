export const contractStatuses = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '待分配' },
  { value: 'ASSIGNED', label: '待会签' },
  { value: 'COUNTERSIGNED', label: '待定稿' },
  { value: 'FINALIZED', label: '待审批' },
  { value: 'APPROVED', label: '待签订' },
  { value: 'SIGNED', label: '已签订' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'RETURNED', label: '已打回' },
  { value: 'CANCELLED', label: '已取消' }
]

export const workflowSteps = [
  { type: 'ASSIGN', label: '分配', status: 'DRAFT' },
  { type: 'COUNTERSIGN', label: '会签', status: 'ASSIGNED' },
  { type: 'FINALIZE', label: '定稿', status: 'COUNTERSIGNED' },
  { type: 'APPROVAL', label: '审批', status: 'FINALIZED' },
  { type: 'SIGN', label: '签订', status: 'APPROVED' }
]

export const taskOrder = { ASSIGN: 1, COUNTERSIGN: 2, REVISE: 3, FINALIZE: 4, APPROVAL: 5, SIGN: 6 }
export const taskLabels = { ASSIGN: '分配', COUNTERSIGN: '会签', REVISE: '起草处理', FINALIZE: '定稿', APPROVAL: '审批', SIGN: '签订' }

export function contractStatusLabel(status) {
  return contractStatuses.find(item => item.value === status)?.label || status || '-'
}

export function taskLabel(taskType) {
  return taskLabels[taskType] || taskType || '-'
}

export function taskStatusLabel(status) {
  const map = { PENDING: '待处理', DONE: '已完成', REJECTED: '已拒绝/打回', SUPERSEDED: '已封存' }
  return map[status] || status || '-'
}

export function activeStepIndex(status, returnTargetStage = '') {
  if (status === 'DRAFT') return 0
  if (status === 'ASSIGNED') return 1
  if (status === 'COUNTERSIGNED') return 2
  if (status === 'FINALIZED') return 3
  if (status === 'APPROVED') return 4
  if (status === 'SIGNED') return 5
  if (status === 'REJECTED') return 3
  if (status === 'RETURNED') return returnTargetStage === 'DRAFT' ? 0 : 2
  return 0
}
