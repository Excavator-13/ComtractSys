<script setup>
import { computed } from 'vue'
import { activeStepIndex, taskLabel, workflowSteps } from '../constants/contract'

const props = defineProps({
  contract: { type: Object, required: true },
  tasks: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false }
})

const currentIndex = computed(() => activeStepIndex(props.contract?.status))

function tasksFor(type) {
  return props.tasks.filter(task => task.taskType === type && Number(task.round || 1) === Number(props.contract?.currentRound || 1))
}

function stepState(index, step) {
  if (props.contract?.status === 'REJECTED' && step.type === 'APPROVAL') return 'rejected'
  if (props.contract?.status === 'RETURNED' && index === currentIndex.value) return 'returned'
  if (props.contract?.status === 'SIGNED' || index < currentIndex.value) return 'done'
  if (index === currentIndex.value) return 'active'
  return 'idle'
}

function summary(step) {
  const items = tasksFor(step.type)
  if (!items.length) return step.type === 'ASSIGN' ? '待分配' : '未开始'
  const done = items.filter(task => ['DONE', 'REJECTED'].includes(task.taskStatus)).length
  const pending = items.filter(task => task.taskStatus === 'PENDING').map(task => task.assigneeName).join('、')
  if (pending) return `待 ${pending}`
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
      :title="taskLabel(step.type) + '：' + summary(step)"
    >
      <span class="pipeline-dot">{{ stepState(index, step) === 'done' ? '✓' : index + 1 }}</span>
      <span class="pipeline-label">{{ step.label }}</span>
      <small v-if="!compact">{{ summary(step) }}</small>
    </div>
  </div>
</template>
