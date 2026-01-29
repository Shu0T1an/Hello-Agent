import { ref, computed, onMounted, onUnmounted } from 'vue'

export interface Breakpoints {
  isMobile: boolean
  isTablet: boolean
  isDesktop: boolean
  width: number
}

const windowWidth = ref(window.innerWidth)

// 断点定义
const BREAKPOINTS = {
  mobile: 640,
  tablet: 1024,
}

export function useBreakpoints() {
  const updateWidth = () => {
    windowWidth.value = window.innerWidth
  }

  onMounted(() => {
    window.addEventListener('resize', updateWidth)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateWidth)
  })

  const isMobile = computed(() => windowWidth.value < BREAKPOINTS.mobile)
  const isTablet = computed(
    () => windowWidth.value >= BREAKPOINTS.mobile && windowWidth.value < BREAKPOINTS.tablet
  )
  const isDesktop = computed(() => windowWidth.value >= BREAKPOINTS.tablet)
  const width = computed(() => windowWidth.value)

  return {
    isMobile,
    isTablet,
    isDesktop,
    width,
  }
}
