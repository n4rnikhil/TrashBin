"use client";

import { X } from "lucide-react";
import { useId, useState } from "react";
import type { KeyboardEvent } from "react";
import { Input } from "@/components/ui/input";
import { TagChip } from "@/features/tags";

export const MAX_TAGS = 20;
export const MAX_TAG_LENGTH = 50;

const clean = (raw: string) => raw.replace(/\s+/g, " ").trim();

/**
 * Chip input for a file's tags. Enter or comma adds a tag, Backspace on an empty field removes the
 * last one; existing tags are offered as suggestions. Names compare case-insensitively, like the
 * server. The server remains the authority on limits.
 */
export function TagEditor({
  value,
  onChange,
  suggestions,
  colors = {},
}: {
  value: string[];
  onChange: (tags: string[]) => void;
  suggestions: string[];
  /** Known colors by lower-case name, for nicer chips. */
  colors?: Record<string, string | null>;
}) {
  const [draft, setDraft] = useState("");
  const [hint, setHint] = useState<string | null>(null);
  const listId = useId();
  const inputId = useId();

  const add = (raw: string) => {
    const name = clean(raw);
    if (!name) return;
    if (name.length > MAX_TAG_LENGTH) {
      setHint(`Tags can be at most ${MAX_TAG_LENGTH} characters.`);
      return;
    }
    if (value.some((tag) => tag.toLowerCase() === name.toLowerCase())) {
      setDraft("");
      return;
    }
    if (value.length >= MAX_TAGS) {
      setHint(`A file can have at most ${MAX_TAGS} tags.`);
      return;
    }
    setHint(null);
    onChange([...value, name]);
    setDraft("");
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      add(draft);
    } else if (event.key === "Backspace" && draft === "" && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  };

  const available = suggestions.filter(
    (s) => !value.some((tag) => tag.toLowerCase() === s.toLowerCase()),
  );

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="sr-only">
        Add a tag
      </label>
      {value.length > 0 ? (
        <ul className="flex flex-wrap gap-1.5" aria-label="Tags">
          {value.map((tag) => (
            <li key={tag.toLowerCase()}>
              <TagChip name={tag} color={colors[tag.toLowerCase()] ?? null}>
                <button
                  type="button"
                  aria-label={`Remove tag ${tag}`}
                  onClick={() => onChange(value.filter((t) => t !== tag))}
                  className="hover:text-foreground text-muted-foreground -mr-0.5 rounded-full"
                >
                  <X className="size-3" />
                </button>
              </TagChip>
            </li>
          ))}
        </ul>
      ) : null}
      <Input
        id={inputId}
        list={listId}
        value={draft}
        placeholder={value.length >= MAX_TAGS ? "Tag limit reached" : "Add a tag and press Enter"}
        disabled={value.length >= MAX_TAGS}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => add(draft)}
        aria-describedby={hint ? `${inputId}-hint` : undefined}
      />
      <datalist id={listId}>
        {available.map((s) => (
          <option key={s} value={s} />
        ))}
      </datalist>
      {hint ? (
        <p id={`${inputId}-hint`} className="text-destructive text-xs">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
