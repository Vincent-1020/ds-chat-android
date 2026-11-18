import { create } from 'zustand'

/** 移动端侧边栏抽屉：顶栏菜单按钮打开，点遮罩、切换会话或开启新对话后关闭 */
export const useMobileSider = create<{
  open: boolean
  progress: number
  dragging: boolean
}>(() => ({ open: false, progress: 0, dragging: false }))

/** 抽屉宽度（与 Sider.module.css 里 .mobile 的 300px / max-width 保持一致） */
export const mobileSiderWidth = (): number =>
  Math.min(300, Math.max(200, window.innerWidth - 48))

const clamp01 = (value: number) => (value < 0 ? 0 : value > 1 ? 1 : value)

export const openMobileSider = () =>
  useMobileSider.setState({ open: true, progress: 1, dragging: false })

export const closeMobileSider = () =>
  useMobileSider.setState({ open: false, progress: 0, dragging: false })

/** 拖拽过程中只更新进度，不改变最终开合状态 */
export const setSiderProgress = (progress: number, dragging = true) =>
  useMobileSider.setState({ progress: clamp01(progress), dragging, open: true })

/** 手势结束：按进度是否过半决定吸附到打开或关闭 */
export const settleSider = (open: boolean) =>
  useMobileSider.setState({ open, progress: open ? 1 : 0, dragging: false })
