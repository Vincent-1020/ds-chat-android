/**
 * Minimal runtime polyfills for older Android WebViews.
 *
 * The upstream demo targets Chrome 105+, but a lot of phones (notably Huawei /
 * Honor devices without an updated "Android System WebView") ship Chromium 6x-8x.
 * SWC lowers the *syntax* for those targets, this file fills in the *APIs*.
 *
 * Everything here is defensive: it never overrides an existing implementation.
 */

type Loose = Record<string, any>

const g = globalThis as unknown as Loose

function define(target: Loose | undefined, name: string, value: unknown): void {
  if (!target || typeof target[name] !== 'undefined') {
    return
  }
  Object.defineProperty(target, name, {
    configurable: true,
    writable: true,
    value,
  })
}

/* ---- Array.prototype.at / String.prototype.at (Chrome 92) ---- */
function atImpl(this: any, index: number) {
  const len = this.length >>> 0
  let i = Math.trunc(index) || 0
  if (i < 0) {
    i += len
  }
  return i < 0 || i >= len ? undefined : this[i]
}
define(Array.prototype as unknown as Loose, 'at', atImpl)
define(String.prototype as unknown as Loose, 'at', atImpl)

/* ---- Array.prototype.findLast / findLastIndex (Chrome 97) ---- */
define(Array.prototype as unknown as Loose, 'findLast', function (this: any[], predicate: any, thisArg?: any) {
  for (let i = this.length - 1; i >= 0; i--) {
    if (predicate.call(thisArg, this[i], i, this)) {
      return this[i]
    }
  }
  return undefined
})
define(Array.prototype as unknown as Loose, 'findLastIndex', function (this: any[], predicate: any, thisArg?: any) {
  for (let i = this.length - 1; i >= 0; i--) {
    if (predicate.call(thisArg, this[i], i, this)) {
      return i
    }
  }
  return -1
})

/* ---- Object.hasOwn (Chrome 93) ---- */
define(Object as unknown as Loose, 'hasOwn', function (obj: unknown, key: PropertyKey) {
  return Object.prototype.hasOwnProperty.call(obj, key)
})

/* ---- Object.fromEntries (Chrome 73) ---- */
define(Object as unknown as Loose, 'fromEntries', function (entries: Iterable<[PropertyKey, any]>) {
  const out: Loose = {}
  for (const pair of entries as any) {
    out[pair[0]] = pair[1]
  }
  return out
})

/* ---- String.prototype.replaceAll (Chrome 85) ---- */
define(String.prototype as unknown as Loose, 'replaceAll', function (
  this: string,
  search: string | RegExp,
  replacement: string,
) {
  if (search instanceof RegExp) {
    return this.replace(search, replacement as string)
  }
  return this.split(String(search)).join(String(replacement))
})

/* ---- Array.prototype.flat / flatMap (Chrome 69) ---- */
define(Array.prototype as unknown as Loose, 'flat', function (this: any[], depth: number = 1) {
  const flatten = (list: any[], d: number): any[] => {
    const out: any[] = []
    for (const item of list) {
      if (Array.isArray(item) && d > 0) {
        out.push(...flatten(item, d - 1))
      } else {
        out.push(item)
      }
    }
    return out
  }
  return flatten(this, Math.trunc(depth))
})
define(Array.prototype as unknown as Loose, 'flatMap', function (this: any[], fn: any, thisArg?: any) {
  return (Array.prototype as any).flat.call(
    this.map((v: any, i: number, arr: any[]) => fn.call(thisArg, v, i, arr)),
    1,
  )
})

/* ---- queueMicrotask (Chrome 71) ---- */
define(globalThis as unknown as Loose, 'queueMicrotask', function (cb: () => void) {
  Promise.resolve().then(cb).catch((err) => setTimeout(() => { throw err }, 0))
})

/* ---- Element.prototype.replaceChildren (Chrome 86) ---- */
if (typeof Element !== 'undefined') {
  define(Element.prototype as unknown as Loose, 'replaceChildren', function (this: Element, ...nodes: any[]) {
    while (this.firstChild) {
      this.removeChild(this.firstChild)
    }
    for (const node of nodes) {
      this.appendChild(typeof node === 'string' ? document.createTextNode(node) : node)
    }
  })
}

/* ---- Promise.allSettled (Chrome 76) ---- */
define(Promise as unknown as Loose, 'allSettled', function (iterable: Iterable<any>) {
  return Promise.all(
    Array.from(iterable).map((p) =>
      Promise.resolve(p).then(
        (value) => ({ status: 'fulfilled', value }),
        (reason) => ({ status: 'rejected', reason }),
      ),
    ),
  )
})

/* ---- crypto.randomUUID (Chrome 92) ---- */
if (typeof crypto !== 'undefined') {
  define(crypto as unknown as Loose, 'randomUUID', function () {
    const bytes = new Uint8Array(16)
    if (typeof crypto.getRandomValues === 'function') {
      crypto.getRandomValues(bytes)
    } else {
      for (let i = 0; i < 16; i++) {
        bytes[i] = Math.floor(Math.random() * 256)
      }
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex: string[] = []
    for (let i = 0; i < 16; i++) {
      hex.push((bytes[i] + 0x100).toString(16).slice(1))
    }
    return (
      hex.slice(0, 4).join('') + '-' + hex.slice(4, 6).join('') + '-' + hex.slice(6, 8).join('') +
      '-' + hex.slice(8, 10).join('') + '-' + hex.slice(10, 16).join('')
    )
  })
}

/* ---- structuredClone (Chrome 98) ---- */
define(globalThis as unknown as Loose, 'structuredClone', function (value: any) {
  return JSON.parse(JSON.stringify(value))
})

/* ---- globalThis (Chrome 71) ---- */
if (typeof g.globalThis === 'undefined') {
  ;(window as unknown as Loose).globalThis = window
}

/* ---- Intl.RelativeTimeFormat guard (Chrome 71) ---- */
if (typeof Intl !== 'undefined' && typeof (Intl as Loose).RelativeTimeFormat === 'undefined') {
  ;(Intl as Loose).RelativeTimeFormat = function (this: Loose) {
    this.format = function () {
      return ''
    }
    this.formatToParts = function () {
      return []
    }
  }
}

export {}
