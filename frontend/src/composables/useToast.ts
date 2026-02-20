import { ref } from 'vue'
import { createApp, h } from 'vue'
import BaseToast from '@/components/base/BaseToast.vue'
import type { ToastProps } from '@/components/base/BaseToast.vue'

export interface ToastOptions {
  type?: 'success' | 'warning' | 'error' | 'info'
  message: string
  duration?: number
  position?: 'top' | 'top-right' | 'top-left' | 'bottom' | 'bottom-right' | 'bottom-left'
  closable?: boolean
  showIcon?: boolean
  onClose?: () => void
  onClick?: () => void
}

interface ToastInstance extends ToastProps {
  id: string
  vm: any
}

const toasts = ref<Map<string, ToastInstance>>(new Map())
let toastIdCounter = 0
let containerEl: HTMLElement | null = null

const ensureContainer = () => {
  if (!containerEl) {
    containerEl = document.createElement('div')
    containerEl.id = 'toast-container'
    containerEl.style.cssText = `
      position: fixed;
      z-index: 1070;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
    `
    document.body.appendChild(containerEl)
  }
  return containerEl
}

const getPositionStyles = (position: string) => {
  const positions = {
    top: 'top: 0; left: 50%; transform: translateX(-50%);',
    'top-right': 'top: 0; right: 0;',
    'top-left': 'top: 0; left: 0;',
    bottom: 'bottom: 0; left: 50%; transform: translateX(-50%);',
    'bottom-right': 'bottom: 0; right: 0;',
    'bottom-left': 'bottom: 0; left: 0;',
  }
  return positions[position as keyof typeof positions] || positions['top-right']
}

const createToast = (options: ToastOptions): string => {
  const id = `toast-${++toastIdCounter}`
  const container = ensureContainer()

  // 更新容器位置
  const position = options.position || 'top-right'
  container.style.cssText = `
    position: fixed;
    z-index: 1070;
    pointer-events: none;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    ${getPositionStyles(position)}
  `

  // 创建 Toast 实例
  const toastProps: ToastProps = {
    id,
    type: options.type || 'info',
    message: options.message,
    duration: options.duration ?? 3000,
    closable: options.closable ?? true,
    showIcon: options.showIcon ?? true,
    onClose: options.onClose,
    onClick: options.onClick,
  }

  // 创建 Vue 应用
  const toastApp = createApp({
    render() {
      return h(BaseToast, {
        ...toastProps,
        onClose: () => {
          toastProps.onClose?.()
          removeToast(id)
        },
      })
    },
  })

  const toastEl = document.createElement('div')
  toastEl.setAttribute('data-toast-id', id)
  toastEl.style.pointerEvents = 'auto'
  container.appendChild(toastEl)
  toastApp.mount(toastEl)

  const toastInstance: ToastInstance = {
    ...toastProps,
    vm: toastApp,
  }

  toasts.value.set(id, toastInstance)

  return id
}

const removeToast = (id: string) => {
  const toast = toasts.value.get(id)
  if (toast) {
    toast.vm.unmount()
    const container = ensureContainer()
    const toastEl = container.querySelector(`[data-toast-id="${id}"]`)
    if (toastEl) {
      toastEl.remove()
    }
    toasts.value.delete(id)

    // 如果没有更多的 toast，移除容器
    if (toasts.value.size === 0) {
      setTimeout(() => {
        if (toasts.value.size === 0 && containerEl) {
          containerEl.remove()
          containerEl = null
        }
      }, 200)
    }
  }
}

const clearAll = () => {
  toasts.value.forEach((_, id) => {
    removeToast(id)
  })
}

export const useToast = () => {
  return {
    show: createToast,
    success: (message: string, options?: Omit<ToastOptions, 'type' | 'message'>) => {
      return createToast({ ...options, message, type: 'success' })
    },
    warning: (message: string, options?: Omit<ToastOptions, 'type' | 'message'>) => {
      return createToast({ ...options, message, type: 'warning' })
    },
    error: (message: string, options?: Omit<ToastOptions, 'type' | 'message'>) => {
      return createToast({ ...options, message, type: 'error' })
    },
    info: (message: string, options?: Omit<ToastOptions, 'type' | 'message'>) => {
      return createToast({ ...options, message, type: 'info' })
    },
    close: removeToast,
    clearAll,
  }
}
