"use client";

import { ChevronDown, Search, Star, X } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { useCollections } from "@/features/collections";
import { TagChip, useTags } from "@/features/tags";
import type { TypeGroup } from "@/lib/api/types";
import type { FileSort } from "../api/files-api";
import { effectiveSort, hasActiveFilters, TYPE_GROUPS, type FileFilters } from "../filters";
import { TYPE_LABELS } from "./file-type-icon";

const SORT_LABELS: Record<FileSort, string> = {
  relevance: "Best match",
  "archivedAt,desc": "Newest first",
  "archivedAt,asc": "Oldest first",
  "displayName,asc": "Name (A–Z)",
  "sizeBytes,desc": "Largest first",
};

const ALL = "__all__";

type Props = {
  filters: FileFilters;
  update: (patch: Partial<FileFilters>) => void;
  clear: () => void;
};

/** Search box, filter menus and removable chips for the active filters. */
export function FileFilterBar({ filters, update, clear }: Props) {
  const tags = useTags();
  const collections = useCollections();
  const tagById = new Map((tags.data ?? []).map((t) => [t.id, t]));
  const collectionName = collections.data?.find((c) => c.id === filters.collection)?.name;
  const sort = effectiveSort(filters);
  const sorts = (Object.keys(SORT_LABELS) as FileSort[]).filter(
    (s) => s !== "relevance" || filters.q,
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-2 sm:flex-row">
        <SearchBox value={filters.q} onSearch={(q) => update({ q })} />
        <div className="flex gap-2">
          <Button
            variant={filters.favorite ? "secondary" : "outline"}
            aria-pressed={filters.favorite}
            onClick={() => update({ favorite: !filters.favorite })}
          >
            <Star
              className={filters.favorite ? "fill-warning text-warning" : undefined}
              aria-hidden
            />
            Favorites
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger render={<Button variant="outline" />}>
              {SORT_LABELS[sort]} <ChevronDown aria-hidden />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-44">
              <DropdownMenuRadioGroup
                value={sort}
                onValueChange={(value: string) =>
                  update({ sort: sorts.find((s) => s === value) ?? null })
                }
              >
                {sorts.map((s) => (
                  <DropdownMenuRadioItem key={s} value={s}>
                    {SORT_LABELS[s]}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="outline" size="sm" />}>
            {filters.typeGroup ? TYPE_LABELS[filters.typeGroup] : "Any type"}
            <ChevronDown aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent className="w-44">
            <DropdownMenuRadioGroup
              value={filters.typeGroup ?? ALL}
              onValueChange={(value: string) =>
                update({ typeGroup: TYPE_GROUPS.find((g) => g === value) ?? null })
              }
            >
              <DropdownMenuRadioItem value={ALL}>Any type</DropdownMenuRadioItem>
              {TYPE_GROUPS.map((group: TypeGroup) => (
                <DropdownMenuRadioItem key={group} value={group}>
                  {TYPE_LABELS[group]}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger
            render={<Button variant="outline" size="sm" disabled={!tags.data?.length} />}
          >
            {filters.tags.length ? `Tags (${filters.tags.length})` : "Tags"}
            <ChevronDown aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent className="max-h-72 w-52 overflow-y-auto">
            <DropdownMenuGroup>
              <DropdownMenuLabel>Files with all of</DropdownMenuLabel>
              {(tags.data ?? []).map((tag) => (
                <DropdownMenuCheckboxItem
                  key={tag.id}
                  checked={filters.tags.includes(tag.id)}
                  onCheckedChange={(checked: boolean) =>
                    update({
                      tags: checked
                        ? [...filters.tags, tag.id].slice(0, 10)
                        : filters.tags.filter((id) => id !== tag.id),
                    })
                  }
                >
                  {tag.name}
                </DropdownMenuCheckboxItem>
              ))}
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger
            render={<Button variant="outline" size="sm" disabled={!collections.data?.length} />}
          >
            {collectionName ?? "Collection"}
            <ChevronDown aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent className="max-h-72 w-52 overflow-y-auto">
            <DropdownMenuRadioGroup
              value={filters.collection ?? ALL}
              onValueChange={(value: string) =>
                update({ collection: value === ALL ? null : value })
              }
            >
              <DropdownMenuRadioItem value={ALL}>Any collection</DropdownMenuRadioItem>
              {(collections.data ?? []).map((c) => (
                <DropdownMenuRadioItem key={c.id} value={c.id}>
                  {c.name}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <label className="text-muted-foreground flex items-center gap-1.5 text-sm">
          From
          <Input
            type="date"
            className="h-8 w-auto"
            value={filters.from ?? ""}
            max={filters.to ?? undefined}
            onChange={(event) => update({ from: event.target.value || null })}
          />
        </label>
        <label className="text-muted-foreground flex items-center gap-1.5 text-sm">
          To
          <Input
            type="date"
            className="h-8 w-auto"
            value={filters.to ?? ""}
            min={filters.from ?? undefined}
            onChange={(event) => update({ to: event.target.value || null })}
          />
        </label>
      </div>

      {hasActiveFilters(filters) ? (
        <ul className="flex flex-wrap items-center gap-1.5" aria-label="Active filters">
          {filters.q ? (
            <FilterChip label={`“${filters.q}”`} onRemove={() => update({ q: "" })} />
          ) : null}
          {filters.typeGroup ? (
            <FilterChip
              label={TYPE_LABELS[filters.typeGroup]}
              onRemove={() => update({ typeGroup: null })}
            />
          ) : null}
          {filters.tags.map((id) => {
            const tag = tagById.get(id);
            return (
              <li key={id}>
                <TagChip name={tag?.name ?? "Unknown tag"} color={tag?.color ?? null}>
                  <RemoveButton
                    label={`Remove tag filter ${tag?.name ?? ""}`}
                    onClick={() => update({ tags: filters.tags.filter((t) => t !== id) })}
                  />
                </TagChip>
              </li>
            );
          })}
          {filters.collection ? (
            <FilterChip
              label={`Collection: ${collectionName ?? "…"}`}
              onRemove={() => update({ collection: null })}
            />
          ) : null}
          {filters.from || filters.to ? (
            <FilterChip
              label={`${filters.from ?? "…"} – ${filters.to ?? "…"}`}
              onRemove={() => update({ from: null, to: null })}
            />
          ) : null}
          {filters.favorite ? (
            <FilterChip label="Favorites" onRemove={() => update({ favorite: false })} />
          ) : null}
          <li>
            <Button variant="link" size="sm" onClick={clear}>
              Clear all
            </Button>
          </li>
        </ul>
      ) : null}
    </div>
  );
}

/** Searches on Enter; keeps in step when the URL changes (e.g. "Clear all"). */
function SearchBox({ value, onSearch }: { value: string; onSearch: (q: string) => void }) {
  const [draft, setDraft] = useState(value);
  const [lastValue, setLastValue] = useState(value);
  if (value !== lastValue) {
    // Adjust local state during render when the source of truth changed elsewhere.
    setLastValue(value);
    setDraft(value);
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSearch(draft.trim());
  };

  return (
    <form role="search" onSubmit={submit} className="relative flex-1">
      <Search
        className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
        aria-hidden
      />
      <Input
        type="search"
        aria-label="Search your archive"
        placeholder="Search names, notes and tags — press Enter"
        className="pl-9"
        maxLength={200}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
    </form>
  );
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <li className="bg-secondary text-secondary-foreground inline-flex items-center gap-1 rounded-full py-0.5 pr-1.5 pl-2.5 text-xs font-medium">
      {label}
      <RemoveButton label={`Remove filter ${label}`} onClick={onRemove} />
    </li>
  );
}

function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="text-muted-foreground hover:text-foreground rounded-full"
    >
      <X className="size-3" />
    </button>
  );
}
