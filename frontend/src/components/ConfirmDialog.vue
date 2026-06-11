<script setup>
defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' }
})
defineEmits(['confirm', 'cancel'])
</script>

<template>
  <div v-if="show" class="modal-mask">
    <div class="confirm-dialog">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      <div class="row-actions">
        <button class="primary" @click="$emit('confirm')">确认</button>
        <button class="secondary" @click="$emit('cancel')">取消</button>
      </div>
    </div>
  </div>
</template>
