import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { DumGroup } from "@/models/auth/group";
import type { DumRole } from "@/models/auth/role";
import { DumRoleService } from "@/services/auth/role.service";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

const roleService = new DumRoleService();

interface AdminGroupFormProps {
  group: DumGroup;
  isNew: boolean;
  onSave: (group: DumGroup) => Promise<void> | void;
  onDelete?: () => Promise<void> | void;
  onCancel: () => void;
}

interface FormValues {
  name: string;
  description: string;
  roleIds: string[];
}

export function AdminGroupForm({ group, isNew, onSave, onDelete, onCancel }: AdminGroupFormProps) {
  const { t } = useTranslation();
  const [roles, setRoles] = useState<DumRole[]>([]);

  const form = useForm<FormValues>({
    defaultValues: {
      name: group.name ?? "",
      description: group.description ?? "",
      roleIds: (group.dumRoles ?? []).map((r) => r.id),
    },
  });

  useEffect(() => {
    roleService.query().then(setRoles).catch(() => setRoles([]));
  }, []);

  async function onSubmit(values: FormValues) {
    const selectedRoles = roles.filter((r) => values.roleIds.includes(r.id));
    const payload: DumGroup = {
      ...group,
      name: values.name,
      description: values.description,
      dumRoles: selectedRoles,
    };
    await onSave(payload);
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6 max-w-3xl">
        <Card>
          <CardContent className="pt-6 space-y-6">
            <FormField
              control={form.control}
              name="name"
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.groups.name")}</FormLabel>
                  <FormControl>
                    <Input {...field} autoComplete="off" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.groups.description")}</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={3} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="space-y-2">
              <div className="text-sm font-medium">{t("admin.groups.roles")}</div>
              <FormField
                control={form.control}
                name="roleIds"
                render={({ field }) => (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    {roles.map((r) => {
                      const checked = field.value.includes(r.id);
                      return (
                        <label
                          key={r.id}
                          className="flex items-center gap-2 rounded-lg border px-3 py-2 cursor-pointer hover:bg-accent"
                        >
                          <Checkbox
                            checked={checked}
                            onCheckedChange={(c) => {
                              const next = c
                                ? [...field.value, r.id]
                                : field.value.filter((id: string) => id !== r.id);
                              field.onChange(next);
                            }}
                          />
                          <span className="text-sm">
                            <span className="font-medium">{r.name}</span>
                            {r.description && (
                              <span className="ml-2 text-xs text-muted-foreground">{r.description}</span>
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
            {onDelete && !isNew && (
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
