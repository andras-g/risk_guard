import type { Plugin } from "@opencode-ai/plugin"

export const NotificationPlugin: Plugin = async ({ $ }) => {
  return {
    event: async ({ event }) => {
      if (event.type === "session.idle") {
        await $`notify-send -u normal -i dialog-information "OpenCode" "Session idle - ready for input"`
      }

      if (event.type === "session.error") {
        await $`notify-send -u critical -i dialog-error "OpenCode" "Session error occurred!"`
      }

      if (event.type === "permission.asked") {
        await $`notify-send -u critical -i dialog-question "OpenCode" "Permission required - action needed!"`
      }
    },
  }
}
