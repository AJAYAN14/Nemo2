"use client";

import React from "react";
import { motion } from "framer-motion";
import { Slot } from "@radix-ui/react-slot";
import { clsx } from "clsx";
import styles from "./ClayButton.module.css";

interface ClayButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "outline";
  asChild?: boolean;
  className?: string;
}

const ClayButton = React.forwardRef<HTMLButtonElement, ClayButtonProps>(
  ({ className, variant = "primary", asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";

    return (
      <motion.div
        whileTap={{ scale: 0.92 }}
        transition={{ type: "spring", stiffness: 400, damping: 17 }}
        className={clsx(styles.clayButton, styles[variant], className)}
      >
        <Comp ref={ref} {...props} />
      </motion.div>
    );
  }
);

ClayButton.displayName = "ClayButton";

export { ClayButton };
