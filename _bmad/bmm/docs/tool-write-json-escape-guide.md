# Tool Write JSON Escape Guide

## Problem

When using the `write` or `edit` tool to save large markdown files (especially story files with Dev Notes), the JSON serialization of the `content` parameter frequently fails with:

```
Invalid input for tool write: JSON parsing failed: Expected '}'
```

This happens because the markdown content contains characters that break JSON parsing:
- Unescaped double quotes inside strings
- Backticks in code blocks (especially triple backticks)
- Curly braces `{}` that look like JSON objects
- Backslashes that act as escape characters
- Template variables like `{{variable}}`
- Newlines and special whitespace

## Solution: Write in Smaller Chunks

**NEVER try to write the entire story file in one `write` call.**

Instead:

1. **Write the initial template** with the header and placeholder sections (small, safe content) using `write`.
2. **Use `edit` with `oldString`/`newString`** to replace each placeholder section ONE AT A TIME.
3. **Keep each edit small** — one section at a time (Acceptance Criteria, then Tasks, then Dev Notes subsections).
4. **For Dev Notes** (the largest section), break it into multiple edits:
   - First edit: "Why This Story Exists" + "Current State Analysis"
   - Second edit: "Key Decisions"
   - Third edit: "Project Structure Notes" (file tables)
   - Fourth edit: "Architecture Compliance Checklist"
   - Fifth edit: "Testing Requirements" + "Library Requirements"
   - Sixth edit: "Previous Story Intelligence" + "Git Intelligence"
   - Seventh edit: "UX Specification References" + "Latest Technical Information"
   - Eighth edit: "Project Context Reference" + "Story Completion Status"

5. **Avoid these in content strings:**
   - Raw `\` — use `\\` 
   - Raw `"` inside already-quoted strings — use `\"`
   - Triple backticks — if needed, keep code blocks very short or use indented code blocks instead
   - `${}` template literals — these can confuse JSON parsers

6. **If an edit fails**, make the content chunk even smaller and retry.

## Key Rule

**Small, incremental edits > one giant write. Always.**
