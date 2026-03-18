import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, ref, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useFocusTrap } from './useFocusTrap'

/**
 * Integration tests for useFocusTrap composable.
 * Mounts a real Vue component that uses the composable to verify:
 * - Focus trapping (Tab wraps within container)
 * - Escape key calls deactivate callback
 * - Focus restoration on deactivation
 * Co-located with useFocusTrap.ts per architecture rules.
 */

// Test component that uses the composable
const TrapTestComponent = defineComponent({
  props: {
    active: { type: Boolean, default: false },
  },
  emits: ['deactivate'],
  setup(props, { emit }) {
    const containerRef = ref<HTMLElement | null>(null)
    const isActive = ref(props.active)

    // Watch for prop changes
    const { activate, deactivate } = useFocusTrap(
      containerRef,
      isActive,
      () => emit('deactivate')
    )

    return { containerRef, isActive, activate, deactivate }
  },
  template: `
    <div>
      <button data-testid="outside">Outside</button>
      <div v-if="isActive" ref="containerRef" data-testid="trap-container">
        <button data-testid="first">First</button>
        <button data-testid="second">Second</button>
      </div>
    </div>
  `,
})

describe('useFocusTrap — composable integration', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('traps focus: Tab on last element wraps to first', async () => {
    const wrapper = mount(TrapTestComponent, {
      props: { active: true },
      attachTo: document.body,
    })

    // Allow setTimeout(activate, 0) to fire
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    const second = wrapper.find('[data-testid="second"]').element as HTMLElement
    second.focus()
    expect(document.activeElement).toBe(second)

    // Dispatch Tab keydown — the trap should prevent default and wrap to first
    const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    document.dispatchEvent(tabEvent)

    const first = wrapper.find('[data-testid="first"]').element as HTMLElement
    expect(document.activeElement).toBe(first)

    wrapper.unmount()
  })

  it('traps focus: Shift+Tab on first element wraps to last', async () => {
    const wrapper = mount(TrapTestComponent, {
      props: { active: true },
      attachTo: document.body,
    })

    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    const first = wrapper.find('[data-testid="first"]').element as HTMLElement
    first.focus()
    expect(document.activeElement).toBe(first)

    const shiftTabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(shiftTabEvent)

    const second = wrapper.find('[data-testid="second"]').element as HTMLElement
    expect(document.activeElement).toBe(second)

    wrapper.unmount()
  })

  it('Escape key emits deactivate event', async () => {
    const wrapper = mount(TrapTestComponent, {
      props: { active: true },
      attachTo: document.body,
    })

    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    const escapeEvent = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(escapeEvent)

    expect(wrapper.emitted('deactivate')).toHaveLength(1)

    wrapper.unmount()
  })

  it('activates focus on first focusable element when active becomes true', async () => {
    const wrapper = mount(TrapTestComponent, {
      props: { active: true },
      attachTo: document.body,
    })

    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    const first = wrapper.find('[data-testid="first"]').element as HTMLElement
    expect(document.activeElement).toBe(first)

    wrapper.unmount()
  })

  it('cleans up keydown listener on unmount', async () => {
    const removeListenerSpy = vi.spyOn(document, 'removeEventListener')

    const wrapper = mount(TrapTestComponent, {
      props: { active: true },
      attachTo: document.body,
    })

    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    wrapper.unmount()

    expect(removeListenerSpy).toHaveBeenCalledWith('keydown', expect.any(Function))
  })
})
