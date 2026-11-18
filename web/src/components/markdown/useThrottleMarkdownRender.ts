import { useEffect, useRef, useState } from 'react'

/** 两次更新之间至少间隔的时间，避免低端设备被渲染占满 */
const MIN_WAIT = 16
/**
 * 两次更新的最长间隔。上游默认 2000ms，在手机上会让文字成块地"跳"出来，
 * 观感很卡；这里压到 120ms，保证流式输出是连续推进的。
 */
const MAX_WAIT = 120
/** 上次渲染耗时的倍数，过了这段时间才允许下次更新 */
const TIME_CONSUMING_RATE = 1

/**
 * 流式过程中按渲染耗时节流 Markdown 内容的更新：
 * 更新输出时记录开始时间，提交后按耗时 × TIME_CONSUMING_RATE（夹在 MIN_WAIT 与
 * MAX_WAIT 之间）算出下次允许更新的时间点；新内容在到期时更新，连续输入合并为最新内容。
 * 关闭节流时直接返回最新内容。
 */
export const useThrottleMarkdownRender = (content: string, enable: boolean) => {
  // 首次渲染从挂载时开始计时
  const startRef = useRef(performance.now())
  const updatableAtRef = useRef(0)
  const [output, setOutput] = useState(content)

  useEffect(() => {
    const now = performance.now()
    const cost = (now - startRef.current) * TIME_CONSUMING_RATE
    updatableAtRef.current = now + Math.min(MAX_WAIT, Math.max(MIN_WAIT, cost))
  }, [output])

  useEffect(() => {
    if (!enable) {
      return
    }
    const update = () => {
      startRef.current = performance.now()
      setOutput(content)
    }
    const delay = updatableAtRef.current - performance.now()
    if (delay <= 0) {
      update()
      return
    }
    const timer = setTimeout(update, delay)
    return () => clearTimeout(timer)
  }, [content, enable])

  return enable ? output : content
}
