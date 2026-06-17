<script setup>
import { computed } from 'vue'
import { activeStepIndex, taskLabel, workflowSteps } from '../constants/contract'

const props = defineProps({
  contract: { type: Object, required: true },
  tasks: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false }
})

const currentIndex = computed(() => activeStepIndex(props.contract?.status, props.contract?.returnTargetStage))
const draftResumePending = computed(() =>
  props.contract?.status === 'DRAFT' && props.contract?.returnTargetStage === 'DRAFT'
)

function tasksFor(type) {
  return props.tasks.filter(task =>
    task.taskType === type &&
    task.taskStatus !== 'SUPERSEDED' &&
    Number(task.round || 1) === Number(props.contract?.currentRound || 1)
  )
}

function preservedTasksFor(type) {
  if (!draftResumePending.value) return []
  return props.tasks.filter(task =>
    task.taskType === type &&
    task.taskStatus === 'SUPERSEDED' &&
    Number(task.round || 1) === Number(props.contract?.currentRound || 1)
  )
}

function stepState(index, step) {
  if (props.contract?.status === 'REJECTED' && step.type === 'APPROVAL') return 'rejected'
  if (props.contract?.status === 'RETURNED' && index === currentIndex.value) return 'returned'
  if (draftResumePending.value && step.type === 'ASSIGN') return 'done'
  if (props.contract?.status === 'SIGNED' || index < currentIndex.value) return 'done'
  if (index === currentIndex.value) return 'active'
  return 'idle'
}

function summary(step, index) {
  const items = tasksFor(step.type)
  const preserved = preservedTasksFor(step.type)
  if (draftResumePending.value && step.type === 'ASSIGN') return '已保留分配'
  if (draftResumePending.value && step.type === 'COUNTERSIGN') return '待恢复会签'
  if (props.contract?.status === 'REJECTED' && step.type === 'APPROVAL') return '已拒绝'
  if (props.contract?.status === 'RETURNED' && index === currentIndex.value) return '已打回'
  if (!items.length && preserved.length) {
    const names = preserved.map(task => task.assigneeName).join('、')
    return names ? `已保留 ${names}` : '已保留'
  }
  if (!items.length) return step.type === 'ASSIGN' ? '待分配' : '未开始'
  const done = items.filter(task => task.taskStatus === 'DONE').length
  const rejected = items.filter(task => task.taskStatus === 'REJECTED').length
  const pending = items.filter(task => task.taskStatus === 'PENDING').map(task => task.assigneeName).join('、')
  if (pending) return `待 ${pending}`
  if (rejected) return `${rejected}/${items.length} 已打回`
  return `${done}/${items.length} 完成`
}
</script>

<template>
  <div class="pipeline" :class="{ compact }">
    <div
      v-for="(step, index) in workflowSteps"
      :key="step.type"
      class="pipeline-step"
      :class="stepState(index, step)"
      :title="taskLabel(step.type) + '：' + summary(step, index)"
    >
      <span class="pipeline-dot">{{ stepState(index, step) === 'done' ? '✓' : index + 1 }}</span>
      <span class="pipeline-label">{{ step.label }}</span>
      <small v-if="!compact">{{ summary(step, index) }}</small>
    </div>
  </div>
</template>
