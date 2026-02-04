import { ref, onMounted, watch } from 'vue'

export type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'app-theme'

// 全局共享的主题状态 - 默认深色极客风格
const isDark = ref<Theme>('dark')

export function useTheme() {
  // 初始化主题
  const initTheme = () => {
    const stored = localStorage.getItem(THEME_STORAGE_KEY) as Theme | null
    if (stored && (stored === 'light' || stored === 'dark')) {
      isDark.value = stored
    } else {
      // 默认深色主题，不再跟随系统
      isDark.value = 'dark'
    }
    applyTheme()
  }

  // 应用主题到 DOM
  const applyTheme = () => {
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', isDark.value)
    }
  }

  // 切换主题
  const toggleTheme = () => {
    isDark.value = isDark.value === 'light' ? 'dark' : 'light'
    localStorage.setItem(THEME_STORAGE_KEY, isDark.value)
    applyTheme()
  }

  // 设置主题
  const setTheme = (theme: Theme) => {
    isDark.value = theme
    localStorage.setItem(THEME_STORAGE_KEY, theme)
    applyTheme()
  }

  // 监听系统主题变化
  const watchSystemTheme = () => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      // 只有在用户没有手动设置过主题时才跟随系统
      const hasStoredTheme = localStorage.getItem(THEME_STORAGE_KEY)
      if (!hasStoredTheme) {
        isDark.value = e.matches ? 'dark' : 'light'
        applyTheme()
      }
    }
    mediaQuery.addEventListener('change', handler)
    return () => mediaQuery.removeEventListener('change', handler)
  }

  return {
    isDark,
    toggleTheme,
    setTheme,
    initTheme,
    watchSystemTheme,
  }
}
