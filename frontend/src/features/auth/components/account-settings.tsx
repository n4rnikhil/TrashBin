"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MonitorSmartphone } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { EmptyState } from "@/components/patterns/empty-state";
import { ListSkeleton } from "@/components/patterns/skeletons";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Text } from "@/components/ui/typography";
import { errorMessage } from "@/lib/api/errors";
import { authApi } from "../api/auth-api";
import {
  changePasswordSchema,
  profileSchema,
  type ChangePasswordValues,
  type ProfileValues,
} from "../schemas/auth-schemas";
import { useAuth, useCurrentUser } from "./auth-provider";
import { applyApiError, FormAlert, SubmitButton, TextField } from "./form-parts";

const SESSIONS_KEY = ["auth", "sessions"] as const;

export function ProfileCard() {
  const user = useCurrentUser();
  const { setUser } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting, isDirty },
    reset,
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: user.displayName },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const updated = await authApi.updateProfile(values.displayName);
      setUser(updated);
      reset({ displayName: updated.displayName });
      toast.success("Profile updated");
    } catch (error) {
      applyApiError(error, setError, ["displayName"]);
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>Signed in as {user.email}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="flex max-w-md flex-col gap-4">
          <FormAlert message={errors.root?.message} />
          <TextField
            id="profile-name"
            label="Name"
            autoComplete="name"
            error={errors.displayName?.message}
            {...register("displayName")}
          />
          <SubmitButton
            pending={isSubmitting}
            disabled={isSubmitting || !isDirty}
            className="self-start"
          >
            Save changes
          </SubmitButton>
        </form>
      </CardContent>
    </Card>
  );
}

export function PasswordCard() {
  const { clearSession } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await authApi.changePassword(values);
      toast.success("Password changed", { description: "Sign in again with your new password." });
      clearSession(); // the server ended every session, including this one
    } catch (error) {
      applyApiError(error, setError, ["currentPassword", "newPassword"]);
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Password</CardTitle>
        <CardDescription>Changing it signs you out on every device.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="flex max-w-md flex-col gap-4">
          <FormAlert message={errors.root?.message} />
          <TextField
            id="current-password"
            label="Current password"
            type="password"
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register("currentPassword")}
          />
          <TextField
            id="new-password"
            label="New password"
            type="password"
            autoComplete="new-password"
            description="At least 12 characters."
            error={errors.newPassword?.message}
            {...register("newPassword")}
          />
          <SubmitButton pending={isSubmitting} className="self-start">
            Change password
          </SubmitButton>
        </form>
      </CardContent>
    </Card>
  );
}

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });

export function SessionsCard() {
  const { clearSession } = useAuth();
  const queryClient = useQueryClient();
  const sessions = useQuery({
    queryKey: SESSIONS_KEY,
    queryFn: ({ signal }) => authApi.listSessions(signal),
  });

  const revoke = useMutation({
    mutationFn: authApi.revokeSession,
    onSuccess: () => {
      toast.success("Session signed out");
      return queryClient.invalidateQueries({ queryKey: SESSIONS_KEY });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const revokeAll = useMutation({
    mutationFn: authApi.logoutEverywhere,
    onSuccess: clearSession,
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sessions</CardTitle>
        <CardDescription>Browsers and devices currently signed in to your account.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {sessions.isPending ? (
          <ListSkeleton rows={2} />
        ) : sessions.isError ? (
          <FormAlert message={errorMessage(sessions.error)} />
        ) : sessions.data.length === 0 ? (
          <EmptyState
            size="compact"
            icon={MonitorSmartphone}
            title="No active sessions"
            description="Sessions appear here after you sign in."
          />
        ) : (
          <ul className="divide-border flex flex-col divide-y">
            {sessions.data.map((session) => (
              <li key={session.id} className="flex items-center gap-3 py-3">
                <span className="bg-accent text-accent-foreground flex size-9 shrink-0 items-center justify-center rounded-lg">
                  <MonitorSmartphone className="size-4" aria-hidden />
                </span>
                <div className="flex min-w-0 flex-1 flex-col">
                  <Text as="span" className="truncate font-medium">
                    {session.userAgent ?? "Unknown device"}
                  </Text>
                  <Text as="span" variant="caption" tone="muted" className="font-normal">
                    Active {dateTime.format(new Date(session.lastActiveAt))}
                  </Text>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={revoke.isPending}
                  onClick={() => revoke.mutate(session.id)}
                >
                  Sign out
                </Button>
              </li>
            ))}
          </ul>
        )}
        <Button
          variant="destructive"
          className="self-start"
          disabled={revokeAll.isPending}
          onClick={() => revokeAll.mutate()}
        >
          Sign out everywhere
        </Button>
      </CardContent>
    </Card>
  );
}
