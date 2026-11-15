"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { authApi } from "../api/auth-api";
import { registerSchema, type RegisterValues } from "../schemas/auth-schemas";
import { useAuth } from "./auth-provider";
import { applyApiError, FormAlert, SubmitButton, TextField } from "./form-parts";

export function RegisterForm() {
  const { login } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await authApi.register(values);
      await login(values.email, values.password);
    } catch (error) {
      applyApiError(error, setError, ["displayName", "email", "password"]);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <FormAlert message={errors.root?.message} />
      <TextField
        id="displayName"
        label="Name"
        autoComplete="name"
        autoFocus
        error={errors.displayName?.message}
        {...register("displayName")}
      />
      <TextField
        id="email"
        label="Email"
        type="email"
        autoComplete="email"
        error={errors.email?.message}
        {...register("email")}
      />
      <TextField
        id="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        description="At least 12 characters. A long phrase works well."
        error={errors.password?.message}
        {...register("password")}
      />
      <SubmitButton pending={isSubmitting} size="lg" className="w-full">
        Create account
      </SubmitButton>
      <p className="text-muted-foreground text-center text-sm">
        Already have an account?{" "}
        <Link href="/login" className="text-primary font-medium underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}
