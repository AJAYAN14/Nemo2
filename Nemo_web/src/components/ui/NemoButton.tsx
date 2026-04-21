"use client";

import React from "react";
import { Slot } from "@radix-ui/react-slot";
import { clsx } from "clsx";
import styles from "./NemoButton.module.css";

interface NemoButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "outline";
  asChild?: boolean;
  className?: string;
}

const NemoButton = React.forwardRef<HTMLButtonElement, NemoButtonProps>(
  ({ className, variant = "primary", asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";

    return (
      <Comp
        ref={ref}
        className={clsx(styles.button, styles[variant], className)}
        {...props}
      />
    );
  }
);

NemoButton.displayName = "NemoButton";

export { NemoButton };
