import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { DumPrivilege, DumRole } from "@/models/auth/role";
import { DumPrivilegeService } from "@/services/auth/privilege.service";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

const privilegeService = new DumPrivilegeService();

interface AdminRoleFormProps {
  role: DumRole;
  isNew: boolean;
  onSave: (role: DumRole) => Promise<void> | void;
  onDelete?: () => Promise<void> | void;
  onCancel: () => void;
}

interface FormValues {
  name: string;
  description: string;
  privilegeIds: string[];
}

export function AdminRoleForm({ role, isNew, onSave, onDelete, onCancel }: AdminRoleFormProps) {
  const { t } = useTranslation();
  const [privileges, setPrivileges] = useState<DumPrivilege[]>([]);

  const form = useForm<FormValues>({
    defaultValues: {
      name: role.name ?? "",
      description: role.description ?? "",
      privilegeIds: (role.dumPrivileges ?? []).map((p) => p.id),
    },
  });

  useEffect(() => {
    privilegeService.query().then(setPrivileges).catch(() => setPrivileges([]));
  }, []);

  const byCategory = useMemo(() => {
    const buckets: Record<string, DumPrivilege[]> = {};
    for (const p of privileges) {
      const cat = p.category ?? "OTHER";
      (buckets[cat] ??= []).push(p);
    }
    return buckets;
  }, [privileges]);

  async function onSubmit(values: FormValues) {
    const selected = privileges.filter((p) => values.privilegeIds.includes(p.id));
    const payload: DumRole = {
      ...role,
      name: values.name,
      description: values.description,
      dumPrivileges: selected,
    };
    await onSave(payload);
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6 max-w-4xl">
        <Card>
          <CardContent className="pt-6 space-y-6">
            <FormField
              control={form.control}
              name="name"
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.roles.name")}</FormLabel>
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
                  <FormLabel>{t("admin.roles.description")}</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={3} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="space-y-3">
              <div className="text-sm font-medium">{t("admin.roles.privileges")}</div>
              <FormField
                control={form.control}
                name="privilegeIds"
                render={({ field }) => (
                  <div className="space-y-4">
                    {Object.entries(byCategory).map(([category, items]) => (
                      <div key={category}>
                        <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
                          {category}
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                          {items.map((p) => {
                            const checked = field.value.includes(p.id);
                            return (
                              <label
                                key={p.id}
                                className="flex items-start gap-2 rounded-lg border px-3 py-2 cursor-pointer hover:bg-accent"
                              >
                                <Checkbox
                                  checked={checked}
                                  onCheckedChange={(c) => {
                                    const next = c
                                      ? [...field.value, p.id]
                                      : field.value.filter((id: string) => id !== p.id);
                                    field.onChange(next);
                                  }}
                                />
                                <span className="text-sm">
                                  <span className="font-medium">{p.name}</span>
                                  {p.description && (
                                    <span className="block text-xs text-muted-foreground">
                                      {p.description}
                                    </span>
                                  )}
                                </span>
                              </label>
                            );
                          })}
                        </div>
                      </div>
                    ))}
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
