<script setup>
import { contractStatusLabel, taskStatusLabel } from '../constants/contract'

const props = defineProps({
  value: { type: String, required: true },
  type: { type: String, default: 'contract' }
})

function label() {
  return props.type === 'task' ? taskStatusLabel(props.value) : contractStatusLabel(props.value)
}
</script>

<template>
  <span class="status" :class="'status-' + value?.toLowerCase()">{{ label() }}</span>
</template>
