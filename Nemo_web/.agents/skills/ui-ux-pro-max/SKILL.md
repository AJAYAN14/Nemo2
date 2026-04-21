---
name: ui-ux-pro-max
description: "UI/UX Pro Max provides industry-leading design intelligence for web and mobile applications. It includes 67+ UI styles (glassmorphism, bento, etc.), 161+ color palettes, 57+ font pairings, 99+ UX guidelines, and 25+ chart types. Use this skill when the task involves UI structure, visual design decisions, interaction patterns, accessibility audits, or creating professional interfaces. Triggers: 'ui design', 'ux review', 'create dashboard', 'design system', 'color scheme', 'typography pairing', 'improve aesthetics', 'professional UI', 'responsive layout', 'animation logic'."
usage: "Used to design, build, and optimize professional UI/UX for websites and mobile apps. Applies when a task requires visual excellence, user experience improvements, or design-system-level decisions."
---

# UI/UX Pro Max - Design Intelligence

Comprehensive design guide and recommendation engine for web and mobile applications.

## Instruction & Workflow

When this skill is triggered, follow this workflow to ensure high-quality, premium design output:

### 1. Analyze Design Requirements
Extract product type (Productivity, Tool, Entertainment, etc.), target audience, and desired style keywords (Modern, Minimal, Vibrant, Dark Mode).

### 2. Generate/Query Design Knowledge
Use the local search engine to fetch expert recommendations from the database. Note that the scripts are located in `.agent/skills/ui-ux-pro-max/scripts/`.

- **To get a full design system (RECOMMENDED):**
  ```bash
  python .agent/skills/ui-ux-pro-max/scripts/search.py "<product_type> <style_keywords>" --design-system
  ```
- **To search specific domains (style, color, ux, chart, product, typography, google-fonts, web, react):**
  ```bash
  python .agent/skills/ui-ux-pro-max/scripts/search.py "<query>" --domain <domain>
  ```
- **To get stack-specific best practices:**
  ```bash
  python .agent/skills/ui-ux-pro-max/scripts/search.py "<query>" --stack <stack_name>
  ```
  *(Stacks: react, nextjs, vue, svelte, swiftui, react-native, flutter, tailwind, etc.)*

### 3. Apply Professional UI Standards
Regardless of the chosen style, strictly adhere to these 10 core categories:

1.  **Accessibility (CRITICAL)**: Min 4.5:1 contrast, visible focus rings, aria-labels for icons.
2.  **Touch & Interaction**: Min 44x44pt hit targets, 8px+ spacing, async loading feedback.
3.  **Performance**: Image optimization (WebP), lazy loading, CLS < 0.1.
4.  **Style Selection**: NO EMOJIS as structural icons; use SVG (Lucide/Heroicons). Consistent stroke width.
5.  **Layout & Responsive**: Mobile-first, systematic breakpoints, safe-area compliance.
6.  **Typography & Color**: Base 16px, 1.5-1.75 line-height, semantic color tokens.
7.  **Animation**: 150-300ms duration, spring physics for natural feel, respects `prefers-reduced-motion`.
8.  **Forms & Feedback**: Visible labels (not placeholder-only), inline validation on blur, undo support.
9.  **Navigation**: Max 5 top-level items, predictable back behavior, deep-linking support.
10. **Charts & Data**: Match chart type to data, include accessible legends, provide data-table fallbacks.

### 4. Implementation Checklist
Before completing the task, verify:
- [ ] No emojis in navigation or buttons.
- [ ] All interactive elements have pressed states.
- [ ] Safe areas respected (notch/gesture bar).
- [ ] 8dp spacing rhythm maintained.
- [ ] Contrast meets WCAG AA standards in both Light and Dark modes.

## Resources
- **Data Dir**: `.agent/skills/ui-ux-pro-max/data/`
- **Scripts Dir**: `.agent/skills/ui-ux-pro-max/scripts/`
- **Templates Dir**: `.agent/skills/ui-ux-pro-max/templates/`
