---
trigger: always_on
---

# Nemo2 Web-First Development Rules (Complete Departure from Android)

This document serves as the supreme directive for all Nemo2 development. Following the user's explicit command: The project has completely abandoned Android compatibility and transitioned to the "Web Excellence" model. Maintaining legacy mobile logic for the sake of reuse is strictly prohibited. Technical excellence and modern Web best practices are the sole considerations.

---

## 1. Core Philosophy: Web Sovereignty

### Complete De-Androidization
- Do not reference any Android project paths or logic  
  *(e.g., `E:/Web/Nemo_web/Android project/Nemo` is now considered deprecated history)*  
- The use of legacy Android naming conventions, data structures, or redundant abstraction layers is strictly forbidden.

### Native Web Evolution
- If the best Web implementation *(e.g., Suspense, Streaming, Server Actions)* conflicts with legacy Android logic, the Web-native solution must be chosen without compromise.

### Architectural Freedom
- Developers are encouraged to design superior Web-native interaction logic based on intuition and best practices:
  - Multi-column layouts for large screens  
  - Complex hover states  
  - Global keyboard shortcut systems  

---

## 2. Design System: UI/UX PRO MAX (Minimalist Flat)

### Design Standard
- Strictly follow **UI/UX PRO MAX**
- Visual style: **Minimalist Flat**

### Visual Tokens

**Palette**
- High contrast, pure colors:
  - `#4F46E5` (Indigo)
  - `#10B981` (Emerald)
- Background color: `#F9FAFB`

**Shapes**
- Sharp or moderate rounding: `8px–12px`
- Use **1px borders** instead of shadows
- Use **color blocking** to define hierarchy

**Prohibitions**
- ❌ NO shadows  
- ❌ NO blurs  
- ❌ NO pseudo-3D effects  
- Depth must be achieved through:
  - Typography  
  - Spacing  
  - Color  

**Typography**
- Prioritize:
  - `Inter`
  - `Outfit`
- Goal: Premium, geometric feel + maximum legibility

**Micro-interactions**
- Fast and responsive
- Transition duration: **≤150ms**
  - Examples:
    - Hover opacity → `0.8`
    - Border color deepening  

---

## 3. Technical Stack: Modern Web Stack

### Framework
- **Next.js 15+ (App Router)**
- **TypeScript (Strict Mode)**

### Styling
- **Vanilla CSS (CSS Modules)**
- **CSS Variables**
- ❌ Global CSS pollution is strictly prohibited

### Interaction Primitives
- Built on **Radix UI**
  - Dialog
  - Popover
  - Select
  - etc.

### Data Flow
- Use **TanStack Query (React Query)** for Supabase state synchronization

### Database
- **Supabase Nemo2**
  - ID: `fzzkxymwcambugbxfsvj`

### Logic Patterns
- ❌ Abandon OOP inheritance models from Android
- ✅ Use:
  - Pure Functional TypeScript Services  
  - React Hooks  

---

## 4. Responsive & Interaction Rules

### Desktop-First Responsive Design
- No more “upscaled mobile versions”
- Fully leverage desktop space:
  - Sidebars  
  - Split views  
  - Grid-based layouts  
  - Viewport-aware transformations  

### Full-Featured Web Support

**Keyboard Centric**
- `ESC` → Close
- `ENTER` → Submit
- `CMD/CTRL + K` → Global search

**SEO & Performance**
- Use **React Server Components (RSC)** to optimize LCP
- Use **Link Prefetching** for near-instant navigation

### Component Library
- Maintain the **clay component library**
- All features must be assembled using standard components
- Ensure visual consistency across the system

---

## 5. Workflow & Validation

### Success Metrics
- Code reviews evaluate:
  - Modern Web standards adherence  
  - Lighthouse scores  
  - Core Web Vitals  
  - Web Accessibility  

- ❌ NOT evaluated:
  - Parity with Android  

### Asset Generation
- All UI placeholders must be generated using `generate_image`
- Mock data must reflect **realistic Web application scenarios**

### Zero-Placeholder Principle
- All submitted code must be **fully functional**
- ❌ Forbidden:
  - `TODO: sync with Android logic`
  - Any placeholder or incomplete logic  

---