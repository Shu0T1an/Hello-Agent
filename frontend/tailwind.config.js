/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // 基础灰度色系 - Zinc
        zinc: {
          50: '#fafafa',
          100: '#f4f4f5',
          200: '#e4e4e7',
          300: '#d4d4d8',
          400: '#a1a1aa',
          500: '#71717a',
          600: '#52525b',
          700: '#3f3f46',
          800: '#27272a',
          900: '#18181b',
          950: '#09090b',
        },
        // 主题色 - Indigo
        indigo: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          950: '#1e1b4b',
        },
        purple: {
          50: '#faf5ff',
          100: '#f3e8ff',
          200: '#e9d5ff',
          300: '#d8b4fe',
          400: '#c084fc',
          500: '#a855f7',
          600: '#9333ea',
          700: '#7e22ce',
          800: '#6b21a8',
          900: '#581c87',
          950: '#3b0764',
        },
        // 功能性点缀色
        emerald: {
          50: '#ecfdf5',
          100: '#d1fae5',
          200: '#a7f3d0',
          300: '#6ee7b7',
          400: '#34d399',
          500: '#10b981',
          600: '#059669',
          700: '#047857',
          800: '#065f46',
          900: '#064e3b',
          950: '#022c22',
        },
        rose: {
          50: '#fff1f2',
          100: '#ffe4e6',
          200: '#fecdd3',
          300: '#fda4af',
          400: '#fb7185',
          500: '#f43f5e',
          600: '#e11d48',
          700: '#be123c',
          800: '#9f1239',
          900: '#881337',
          950: '#4c0519',
        },
        // 玻璃效果透明色
        glass: {
          50: 'rgba(255, 255, 255, 0.05)',
          100: 'rgba(255, 255, 255, 0.1)',
          150: 'rgba(255, 255, 255, 0.15)',
          200: 'rgba(255, 255, 255, 0.2)',
          250: 'rgba(255, 255, 255, 0.25)',
          300: 'rgba(255, 255, 255, 0.3)',
          400: 'rgba(255, 255, 255, 0.4)',
          500: 'rgba(255, 255, 255, 0.5)',
        },
        // 语义化颜色 - Success
        success: {
          50: '#f0fdf4',
          100: '#dcfce7',
          200: '#bbf7d0',
          300: '#86efac',
          400: '#4ade80',
          500: '#22c55e',
          600: '#16a34a',
          700: '#15803d',
          800: '#166534',
          900: '#14532d',
          950: '#052e16',
        },
        // 语义化颜色 - Warning
        warning: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b',
          600: '#d97706',
          700: '#b45309',
          800: '#92400e',
          900: '#78350f',
          950: '#451a03',
        },
        // 语义化颜色 - Error
        error: {
          50: '#fef2f2',
          100: '#fee2e2',
          200: '#fecaca',
          300: '#fca5a5',
          400: '#f87171',
          500: '#ef4444',
          600: '#dc2626',
          700: '#b91c1c',
          800: '#991b1b',
          900: '#7f1d1d',
          950: '#450a0a',
        },
        // 语义化颜色 - Info
        info: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
          950: '#172554',
        },
      },
      borderRadius: {
        'xl': '12px',
        '2xl': '16px',
        '3xl': '24px',
      },
      boxShadow: {
        'soft': '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
        'medium': '0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -4px rgba(0, 0, 0, 0.08)',
        'hover': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
        'focus': '0 0 0 3px rgba(79, 70, 229, 0.3)',
        'glow': '0 0 20px rgba(79, 70, 229, 0.4)',
        'glow-lg': '0 0 40px rgba(79, 70, 229, 0.3)',
        'glass': '0 8px 32px rgba(0, 0, 0, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 0 0 1px rgba(255, 255, 255, 0.05)',
        'glass-dark': '0 8px 32px rgba(0, 0, 0, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 0 0 1px rgba(255, 255, 255, 0.05)',
        // 霓虹发光效果
        'glow-neon': '0 0 20px rgba(79, 70, 229, 0.5), 0 0 40px rgba(59, 130, 246, 0.3)',
        'glow-neon-lg': '0 0 30px rgba(79, 70, 229, 0.6), 0 0 60px rgba(59, 130, 246, 0.4)',
        'glow-cyan': '0 0 20px rgba(6, 182, 212, 0.5)',
        'glass-deep': '0 8px 32px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.05)',
      },
      backgroundImage: {
        // 原有渐变
        'gradient-primary': 'linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)',
        'gradient-success': 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)',
        'gradient-warning': 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
        'gradient-error': 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
        'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
        // 新配色方案渐变
        'gradient-avatar': 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)',  // indigo-500 to purple-500
        'gradient-glow': 'linear-gradient(135deg, #6366f1 0%, #9333ea 100%)',     // indigo-500 to purple-600
        // 玻璃拟态渐变
        'gradient-violet': 'linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%)',
        'gradient-purple': 'linear-gradient(135deg, #4f46e5 0%, #4338ca 100%)',
        'gradient-shine': 'linear-gradient(135deg, rgba(255,255,255,0.15) 0%, rgba(255,255,255,0) 50%, rgba(255,255,255,0.05) 100%)',
        'gradient-glass': 'linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0.05) 100%)',
        // 霓虹渐变 - 靛蓝色系
        'gradient-neon-primary': 'linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #06b6d4 100%)',
        'gradient-neon-secondary': 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
        'gradient-neon-glow': 'linear-gradient(45deg, #4f46e5, #3b82f6, #06b6d4, #4f46e5)',
        'gradient-neon-cyan': 'linear-gradient(135deg, #06b6d4 0%, #22d3ee 100%)',
        // 背景渐变
        'bg-dark': 'linear-gradient(135deg, #0c0c0e 0%, #09090b 50%, #18181b 100%)',
        'bg-light': 'linear-gradient(135deg, #f8f9fc 0%, #f1f5f9 100%)',
      },
      backdropBlur: {
        'xs': '2px',
        'xl': '24px',
        '2xl': '40px',   // Canvas 风格 - 40px
        '3xl': '48px',
      },
      animation: {
        'fade-in': 'fade-in 0.2s ease-out',
        'fade-out': 'fade-out 0.2s ease-in',
        // Canvas 风格消息入场动画
        'message-in': 'message-in 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        'slide-in-top': 'slide-in-top 0.3s ease-out',
        'slide-in-bottom': 'slide-in-bottom 0.3s ease-out',
        'slide-in-left': 'slide-in-left 0.3s ease-out',
        'slide-in-right': 'slide-in-right 0.3s ease-out',
        'slide-out-top': 'slide-out-top 0.3s ease-in',
        'slide-out-bottom': 'slide-out-bottom 0.3s ease-in',
        'scale-in': 'scale-in 0.2s ease-out',
        'scale-out': 'scale-out 0.2s ease-in',
        'bounce-in': 'bounce-in 0.5s ease-out',
        'shimmer': 'shimmer 2s linear infinite',
        // 新增玻璃拟态动画
        'glow-rotate': 'glow-rotate 3s linear infinite',
        'pulse-glow': 'pulse-glow 8s ease-in-out infinite alternate',
        'pulse-slow': 'pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'fade-out': {
          '0%': { opacity: '1' },
          '100%': { opacity: '0' },
        },
        'slide-in-top': {
          '0%': { transform: 'translateY(-100%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        'slide-in-bottom': {
          '0%': { transform: 'translateY(100%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        'slide-in-left': {
          '0%': { transform: 'translateX(-100%)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
        'slide-in-right': {
          '0%': { transform: 'translateX(100%)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
        'slide-out-top': {
          '0%': { transform: 'translateY(0)', opacity: '1' },
          '100%': { transform: 'translateY(-100%)', opacity: '0' },
        },
        'slide-out-bottom': {
          '0%': { transform: 'translateY(0)', opacity: '1' },
          '100%': { transform: 'translateY(100%)', opacity: '0' },
        },
        'scale-in': {
          '0%': { transform: 'scale(0.9)', opacity: '0' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'scale-out': {
          '0%': { transform: 'scale(1)', opacity: '1' },
          '100%': { transform: 'scale(0.9)', opacity: '0' },
        },
        'bounce-in': {
          '0%': { transform: 'scale(0.3)', opacity: '0' },
          '50%': { transform: 'scale(1.05)' },
          '70%': { transform: 'scale(0.9)' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'shimmer': {
          '0%': { backgroundPosition: '-1000px 0' },
          '100%': { backgroundPosition: '1000px 0' },
        },
        // 新增玻璃拟态动画关键帧
        'glow-rotate': {
          '0%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
          '100%': { backgroundPosition: '0% 50%' },
        },
        'pulse-glow': {
          '0%': { opacity: '0.5', transform: 'scale(1)' },
          '100%': { opacity: '1', transform: 'scale(1.1)' },
        },
        // Canvas 风格消息入场关键帧
        'message-in': {
          '0%': { opacity: '0', transform: 'scale(0.95) translateY(10px)' },
          '100%': { opacity: '1', transform: 'scale(1) translateY(0)' },
        },
      },
      transitionProperty: {
        'height': 'height',
        'spacing': 'margin, padding',
      },
      transitionDuration: {
        '500': '500ms',
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}
