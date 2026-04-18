import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { AvatarField } from "@/components/avatar-field";
import type { DumGroup } from "@/models/auth/group";
import type { DumUser } from "@/models/auth/user";
import { DumGroupService } from "@/services/auth/group.service";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

const groupService = new DumGroupService();

interface AdminUserFormProps {
  user: DumUser;
  isNew: boolean;
  onSave: (user: DumUser) => Promise<void> | void;
  onDelete?: () => Promise<void> | void;
  onCancel: () => void;
}

interface FormValues {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  password?: string;
  avatarUrl?: string;
  groupIds: string[];
}

/**
 * Simplified admin user edit form. Supports create/update, avatar selection
 * (via DiceBear picker), and group membership toggling.
 */
export function AdminUserForm({ user, isNew, onSave, onDelete, onCancel }: AdminUserFormProps) {
  const { t } = useTranslation();
  const [groups, setGroups] = useState<DumGroup[]>([]);
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(user.avatarUrl);

  const form = useForm<FormValues>({
    defaultValues: {
      username: user.username ?? "",
      firstName: user.firstName ?? "",
      lastName: user.lastName ?? "",
      email: user.email ?? "",
      password: "",
      avatarUrl: user.avatarUrl,
      groupIds: (user.dumGroups ?? []).map((g) => g.id),
    },
  });

  useEffect(() => {
    groupService.query().then(setGroups).catch(() => setGroups([]));
  }, []);

  const initials = ((user.firstName?.[0] ?? user.username?.[0] ?? "U") +
    (user.lastName?.[0] ?? "")).toUpperCase();

  async function onSubmit(values: FormValues) {
    const selectedGroups = groups.filter((g) => values.groupIds.includes(g.id));
    const payload: DumUser = {
      ...user,
      username: values.username,
      firstName: values.firstName,
      lastName: values.lastName,
      email: values.email,
      avatarUrl: avatarUrl,
      dumGroups: selectedGroups,
    };
    if (values.password) {
      (payload as DumUser & { password?: string }).password = values.password;
    }
    await onSave(payload);
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6 max-w-3xl">
        <Card>
          <CardContent className="pt-6 space-y-6">
            <AvatarField
              avatarUrl={avatarUrl}
              initials={initials}
              seed={user.username || "new"}
              size="lg"
              onSelect={(url) => setAvatarUrl(url)}
              onRemove={() => setAvatarUrl(undefined)}
            >
              <div className="text-sm font-medium">{t("admin.users.avatar")}</div>
              <div className="text-xs text-muted-foreground">{t("admin.users.avatarHint")}</div>
            </AvatarField>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="username"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("admin.users.username")}</FormLabel>
                    <FormControl>
                      <Input {...field} disabled={!isNew} autoComplete="off" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="email"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("admin.users.email")}</FormLabel>
                    <FormControl>
                      <Input {...field} type="email" autoComplete="off" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("admin.users.firstName")}</FormLabel>
                    <FormControl>
                      <Input {...field} autoComplete="off" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("admin.users.lastName")}</FormLabel>
                    <FormControl>
                      <Input {...field} autoComplete="off" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem className="md:col-span-2">
                    <FormLabel>
                      {isNew ? t("admin.users.password") : t("admin.users.newPassword")}
                    </FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        type="password"
                        autoComplete="new-password"
                        placeholder={isNew ? "" : t("admin.users.leaveBlankHint")}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="space-y-2">
              <div className="text-sm font-medium">{t("admin.users.groups")}</div>
              <FormField
                control={form.control}
                name="groupIds"
                render={({ field }) => (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
                    {groups.map((g) => {
                      const checked = field.value.includes(g.id);
                      return (
                        <label
                          key={g.id}
                          className="flex items-center gap-2 rounded-lg border px-3 py-2 cursor-pointer hover:bg-accent"
                        >
                          <Checkbox
                            checked={checked}
                            onCheckedChange={(c) => {
                              const next = c
                                ? [...field.value, g.id]
                                : field.value.filter((id: string) => id !== g.id);
                              field.onChange(next);
                            }}
                          />
                          <span className="text-sm">
                            <span className="font-medium">{g.name}</span>
                            {g.description && (
                              <span className="ml-2 text-xs text-muted-foreground">{g.description}</span>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                )}
              />
            </div>
          </CardContent>
        </Card>

        <div className="flex items-center justify-between">
          <div>
            {onDelete && (
              <Button type="button" variant="destructive" onClick={onDelete}>
                {t("common.delete")}
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={onCancel}>
              {t("common.cancel")}
            </Button>
            <GradientButton type="submit">{t("common.save")}</GradientButton>
          </div>
        </div>
      </form>
    </Form>
  );
}
