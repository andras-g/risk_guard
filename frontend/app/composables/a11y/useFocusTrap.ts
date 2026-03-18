import { type Ref, onMounted, onUnmounted, watch } from 'vue'

/**
 * Composable that traps keyboard focus within a container element.
 * Tab/Shift+Tab cycles through focusable elements inside the container.
 * Escape key calls the onDeactivate callback.
 * On activation, focus moves to the first focusable element.
 * On deactivation, focus restores to the previously focused element.
 *
 * @param containerRef - Ref to the container DOM element
 * @param active - Ref<boolean> controlling whether the trap is active
 * @param onDeactivate - Callback invoked when Escape is pressed
 */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  active: Ref<boolean>,
  onDeactivate?: () => void
) {
  let previouslyFocused: HTMLElement | null = null

  const FOCUSABLE_SELECTOR = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ')

  function getFocusableElements(): HTMLElement[] {
    if (!containerRef.value) return []
    return Array.from(containerRef.value.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
  }

  function handleKeyDown(event: KeyboardEvent) {
    if (!active.value || !containerRef.value) return

    if (event.key === 'Escape') {
      event.preventDefault()
      onDeactivate?.()
      return
    }

    if (event.key === 'Tab') {
      const focusable = getFocusableElements()
      if (focusable.length === 0) return

      const first = focusable[0]
      const last = focusable[focusable.length - 1]

      if (event.shiftKey) {
        // Shift+Tab: if focus is on first element, wrap to last
        if (document.activeElement === first) {
          event.preventDefault()
          last.focus()
        }
      } else {
        // Tab: if focus is on last element, wrap to first
        if (document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
  }

  function activate() {
    previouslyFocused = document.activeElement as HTMLElement
    document.addEventListener('keydown', handleKeyDown)

    // Focus the first focusable element in the container
    const focusable = getFocusableElements()
    if (focusable.length > 0) {
      focusable[0].focus()
    }
  }

  function deactivate() {
    document.removeEventListener('keydown', handleKeyDown)

    // Restore focus to the previously focused element
    if (previouslyFocused && previouslyFocused.focus) {
      previouslyFocused.focus()
      previouslyFocused = null
    }
  }

  watch(active, (isActive) => {
    if (isActive) {
      // Use nextTick equivalent — slight delay to ensure container is rendered
      setTimeout(activate, 0)
    } else {
      deactivate()
    }
  })

  onMounted(() => {
    if (active.value) {
      setTimeout(activate, 0)
    }
  })

  onUnmounted(() => {
    deactivate()
  })

  return { activate, deactivate }
}
