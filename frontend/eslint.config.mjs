import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import prettier from "eslint-config-prettier/flat";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-non-null-assertion": "error",
      "@typescript-eslint/consistent-type-imports": "error",
      // Feature boundaries (docs/CODING_STANDARDS.md C.3): import a feature only via its index.ts.
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features/*/*"],
              message: "Import features through their public index.ts only.",
            },
          ],
        },
      ],
    },
  },
  {
    // Inside a feature, relative imports of its own files are fine.
    files: ["src/features/**"],
    rules: { "no-restricted-imports": "off" },
  },
  prettier,
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "coverage/**",
    "next-env.d.ts",
    "src/lib/api/generated/**",
  ]),
]);

export default eslintConfig;
