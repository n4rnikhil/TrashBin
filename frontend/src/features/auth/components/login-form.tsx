"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { loginSchema, type LoginValues } from "../schemas/auth-schemas";
import { useAuth } from "./auth-provider";
import { applyApiError, FormAlert, SubmitButton, TextField } from "./form-parts";

export function LoginForm() {
  const { login } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values.email, values.password);
      // <AnonymousOnly> redirects once the session exists.
    } catch (error) {
      applyApiError(error, setError, []);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <FormAlert message={errors.root?.message} />
      <TextField
        id="email"
        label="Email"
        type="email"
        autoComplete="email"
        autoFocus
        error={errors.email?.message}
        {...register("email")}
      />
      <TextField
        id="password"
        label="Password"
        type="password"
        autoComplete="current-password"
        error={errors.password?.message}
        {...register("password")}
      />
      <SubmitButton pending={isSubmitting} size="lg" className="w-full">
        Sign in
      </SubmitButton>
      <p className="text-muted-foreground text-center text-sm">
        New here?{" "}
        <Link
          href="/register"
          className="text-primary font-medium underline-offset-4 hover:underline"
        >
          Create an account
        </Link>
      </p>
    </form>
  );
}
