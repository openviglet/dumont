import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it } from "vitest";

import en from "../src/i18n/locales/en/forms.json";
import pt from "../src/i18n/locales/pt/forms.json";
import { sitesUnavailableReason } from "../src/services/integration/integration-turing-sites.service";

/** D342: the connector's 502 names why Turing's sites could not be listed. */
function failed(data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError("Request failed with status code 502", "ERR_BAD_RESPONSE", config, null, {
    status: 502,
    statusText: "Bad Gateway",
    headers: {},
    config,
    data,
  });
}

describe("sitesUnavailableReason", () => {
  it("reads the reason the connector sent", () => {
    expect(sitesUnavailableReason(failed({ reason: "unreachable" }))).toBe("unreachable");
    expect(sitesUnavailableReason(failed({ reason: "unauthorized", status: 401 }))).toBe("unauthorized");
    expect(sitesUnavailableReason(failed({ reason: "not_found" }))).toBe("not_found");
  });

  it("reads anything it cannot explain as an error, never as no sites", () => {
    expect(sitesUnavailableReason(failed({ reason: "made-up" }))).toBe("error");
    expect(sitesUnavailableReason(failed(undefined))).toBe("error");
    expect(sitesUnavailableReason(new Error("network"))).toBe("error");
  });

  it("has a message for every reason, in both languages and not the same one", () => {
    for (const reason of ["unreachable", "unauthorized", "not_found", "error"] as const) {
      const english = en.forms.wizard.sitesUnavailable[reason];
      const portuguese = pt.forms.wizard.sitesUnavailable[reason];
      expect(english).toBeTruthy();
      expect(portuguese).toBeTruthy();
      expect(portuguese).not.toBe(english);
    }
  });
});
