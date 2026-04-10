import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { IconFolder, IconFolderOpen } from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

interface BrowseNode {
  name: string;
  path: string;
  primaryType: string;
}

interface AemConnectionInfo {
  endpoint: string;
  username: string;
  password: string;
}

interface AemPathBrowserProps {
  value: string;
  onChange: (path: string) => void;
  connection: AemConnectionInfo;
  integrationId: string;
  startPath?: string;
  minPath?: string;
  placeholder?: string;
  className?: string;
}

export function AemPathBrowser({
  value,
  onChange,
  connection,
  integrationId,
  startPath = "/content",
  minPath,
  placeholder = "/content/mysite",
  className,
}: Readonly<AemPathBrowserProps>) {
  const { t } = useTranslation();
  const [browseOpen, setBrowseOpen] = useState(false);
  const [browsing, setBrowsing] = useState(false);
  const [browseNodes, setBrowseNodes] = useState<BrowseNode[]>([]);
  const [browsePath, setBrowsePath] = useState(startPath);

  const handleBrowse = useCallback(
    async (path: string) => {
      setBrowsing(true);
      try {
        const service = new TurIntegrationAemSourceService(integrationId);
        const result = await service.browse({
          endpoint: connection.endpoint,
          username: connection.username,
          password: connection.password,
          path,
        });
        if (result.success) {
          setBrowseNodes(result.children);
          setBrowsePath(path);
        }
      } catch {
        // silently handle
      } finally {
        setBrowsing(false);
      }
    },
    [connection, integrationId],
  );

  const openBrowser = () => {
    setBrowseOpen(true);
    handleBrowse(startPath);
  };

  const selectPath = (path: string) => {
    onChange(path);
    setBrowseOpen(false);
  };

  const canNavigateUp = () => {
    if (minPath && browsePath === minPath) return false;
    return browsePath !== "/";
  };

  const navigateUp = () => {
    const parent = browsePath.substring(0, browsePath.lastIndexOf("/")) || "/";
    if (minPath && !parent.startsWith(minPath) && parent !== minPath) {
      handleBrowse(minPath);
    } else {
      handleBrowse(parent);
    }
  };

  return (
    <div className={className}>
      <div className="flex gap-2">
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          type="text"
          className="flex-1"
        />
        <GradientButton
          type="button"
          variant="outline"
          size="icon"
          onClick={openBrowser}
          disabled={!connection.endpoint || !connection.username}
        >
          <IconFolderOpen className="size-4" />
        </GradientButton>
      </div>

      {browseOpen && (
        <div className="rounded-lg border bg-muted/30 p-3 mt-2">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2 text-sm font-medium min-w-0">
              <IconFolder className="size-4 text-primary shrink-0" />
              <code className="text-xs bg-muted px-1.5 py-0.5 rounded truncate">
                {browsePath}
              </code>
            </div>
            <div className="flex gap-1 shrink-0">
              {canNavigateUp() && (
                <GradientButton
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={navigateUp}
                  disabled={browsing}
                >
                  ..
                </GradientButton>
              )}
              <GradientButton
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setBrowseOpen(false)}
              >
                {t("forms.common.close")}
              </GradientButton>
            </div>
          </div>

          <div className="mb-2">
            <GradientButton
              type="button"
              variant="outline"
              size="sm"
              className="w-full"
              onClick={() => selectPath(browsePath)}
            >
              {t("forms.wizard.useThisPath", { path: browsePath })}
            </GradientButton>
          </div>

          {browsing ? (
            <div className="text-sm text-muted-foreground py-4 text-center">
              {t("forms.wizard.browsing")}
            </div>
          ) : browseNodes.length === 0 ? (
            <div className="text-sm text-muted-foreground py-4 text-center">
              {t("forms.wizard.noChildren")}
            </div>
          ) : (
            <div className="space-y-0.5 max-h-48 overflow-y-auto">
              {browseNodes.map((node) => (
                <button
                  key={node.path}
                  type="button"
                  className="flex items-center gap-2 w-full rounded px-2 py-1.5 hover:bg-accent/50 text-sm text-left cursor-pointer"
                  onClick={() => handleBrowse(node.path)}
                >
                  <IconFolder className="size-3.5 text-muted-foreground shrink-0" />
                  <span className="truncate">{node.name}</span>
                  <span className="text-xs text-muted-foreground shrink-0">
                    {node.primaryType}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
