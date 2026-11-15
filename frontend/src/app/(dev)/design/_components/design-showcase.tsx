"use client";

import {
  Archive,
  ArchiveRestore,
  Download,
  Eye,
  FileArchive,
  FileSpreadsheet,
  FileText,
  Image as ImageIcon,
  Loader2,
  MoreHorizontal,
  Pencil,
  Plus,
  SearchX,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { BrandLockup } from "@/components/layout/brand";
import { EmptyState } from "@/components/patterns/empty-state";
import { PageHeader } from "@/components/patterns/page-header";
import { CardSkeleton, ListSkeleton } from "@/components/patterns/skeletons";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Heading, Text } from "@/components/ui/typography";
import { SAMPLE_FILES, type SampleFile } from "@/dev/fixtures/archive-samples";

/* ---------------------------------------------------------------------------------------------- */

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1 border-b pb-3">
        <Heading level={2}>{title}</Heading>
        <Text tone="muted">{description}</Text>
      </div>
      {children}
    </section>
  );
}

const SWATCHES = [
  ["Canvas", "bg-background ring-1 ring-border"],
  ["Surface", "bg-card ring-1 ring-border"],
  ["Primary", "bg-primary"],
  ["Accent", "bg-accent"],
  ["Muted", "bg-muted"],
  ["Border", "bg-border"],
  ["Success", "bg-success"],
  ["Warning", "bg-warning"],
  ["Info", "bg-info"],
  ["Destructive", "bg-destructive"],
] as const;

const TYPE_ICONS: Record<SampleFile["typeGroup"], LucideIcon> = {
  DOCUMENT: FileText,
  IMAGE: ImageIcon,
  SPREADSHEET: FileSpreadsheet,
  ARCHIVE: FileArchive,
};

const designOnly = () =>
  toast.info("Design preview", { description: "No real action is wired here." });

/** Visual preview of a future file card, fed by static fixtures. Not an application component. */
function SampleFileCard({ file }: { file: SampleFile }) {
  const Icon = TYPE_ICONS[file.typeGroup];
  return (
    <Card className="hover:ring-input group/file transition-shadow duration-200 hover:shadow-sm">
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-start gap-3">
          <span className="bg-accent text-accent-foreground flex size-10 shrink-0 items-center justify-center rounded-lg">
            <Icon className="size-5" strokeWidth={1.75} aria-hidden />
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <Text as="span" className="truncate font-medium">
              {file.name}
            </Text>
            <Text as="span" variant="caption" tone="muted" className="font-normal">
              {file.sizeLabel} · {file.archivedLabel}
            </Text>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${file.name}`} />
              }
            >
              <MoreHorizontal />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-44">
              <DropdownMenuItem onClick={designOnly}>
                <Eye /> Preview
              </DropdownMenuItem>
              <DropdownMenuItem onClick={designOnly}>
                <Download /> Download
              </DropdownMenuItem>
              <DropdownMenuItem onClick={designOnly}>
                <Pencil /> Edit details
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={designOnly}>
                <Trash2 /> Discard
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {file.tags.map((tag) => (
            <Badge key={tag} variant="secondary">
              {tag}
            </Badge>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

/* ---------------------------------------------------------------------------------------------- */

export function DesignShowcase() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-12 px-4 py-10 sm:px-6 md:px-8">
      <div className="flex flex-col gap-6">
        <BrandLockup />
        <PageHeader
          title="Design system"
          description="Tokens and components of the Trash Bin interface. Development only; sample content is static fixture data."
          actions={<Badge variant="warning">Dev only</Badge>}
        />
      </div>

      <Section
        title="Color tokens"
        description="Off-white canvas, white surfaces, indigo accent, slate text."
      >
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {SWATCHES.map(([name, className]) => (
            <div key={name} className="flex flex-col gap-2">
              <div className={`h-14 rounded-lg ${className}`} />
              <Text as="span" variant="caption" tone="muted">
                {name}
              </Text>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Typography" description="Geist with a compact, product-grade scale.">
        <Card>
          <CardContent className="flex flex-col gap-4">
            <Heading level={3} variant="display">
              Stores your trash.
            </Heading>
            <Heading level={3} variant="title">
              Page title — Overview
            </Heading>
            <Heading level={3} variant="heading">
              Section heading — Recently archived
            </Heading>
            <Heading level={3} variant="subheading">
              Subheading — File details
            </Heading>
            <Text variant="body-lg">Large body for introductions and empty states.</Text>
            <Text>Body text is the workhorse: lists, descriptions, form help and dialogs.</Text>
            <Text tone="muted">Muted body for secondary information.</Text>
            <Text variant="caption" tone="muted">
              Caption · 1.2 MB · Archived 3 days ago
            </Text>
            <Text variant="overline" tone="subtle">
              Overline label
            </Text>
          </CardContent>
        </Card>
      </Section>

      <Section
        title="Buttons"
        description="One primary action per view; secondary actions use outline or ghost."
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button>
            <Plus /> Primary
          </Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="outline">Outline</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="destructive">
            <Trash2 /> Destructive
          </Button>
          <Button variant="link">Link</Button>
          <Button disabled>
            <Loader2 className="animate-spin" /> Saving…
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="xs">Extra small</Button>
          <Button size="sm">Small</Button>
          <Button>Default</Button>
          <Button size="lg">Large</Button>
          <Button size="icon" variant="outline" aria-label="Archive">
            <Archive />
          </Button>
        </div>
      </Section>

      <Section
        title="Inputs"
        description="Labels are always visible; help and errors sit beneath the control."
      >
        <Card>
          <CardContent className="grid gap-6 md:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="ds-name">Display name</FieldLabel>
              <Input id="ds-name" placeholder="e.g. Apartment lease 2021" />
              <FieldDescription>Shown instead of the original filename.</FieldDescription>
            </Field>
            <Field data-invalid>
              <FieldLabel htmlFor="ds-tag">Tag</FieldLabel>
              <Input
                id="ds-tag"
                aria-invalid
                defaultValue="a-very-long-tag-name-that-is-too-long"
              />
              <FieldError>Tags can be at most 50 characters.</FieldError>
            </Field>
            <Field className="md:col-span-2">
              <FieldLabel htmlFor="ds-context">Why are you keeping this?</FieldLabel>
              <Textarea id="ds-context" placeholder="A note for future you." rows={3} />
            </Field>
            <Field data-disabled>
              <FieldLabel htmlFor="ds-disabled">Disabled</FieldLabel>
              <Input id="ds-disabled" disabled placeholder="Not editable" />
            </Field>
          </CardContent>
        </Card>
      </Section>

      <Section
        title="Dropdowns and dialogs"
        description="Menus for secondary actions; dialogs for decisions."
      >
        <div className="flex flex-wrap gap-3">
          <DropdownMenu>
            <DropdownMenuTrigger render={<Button variant="outline" />}>Sort by</DropdownMenuTrigger>
            <DropdownMenuContent className="w-52">
              <DropdownMenuGroup>
                <DropdownMenuLabel>Sort by</DropdownMenuLabel>
                <DropdownMenuItem onClick={designOnly}>
                  Recently archived <DropdownMenuShortcut>⌘1</DropdownMenuShortcut>
                </DropdownMenuItem>
                <DropdownMenuItem onClick={designOnly}>
                  Name <DropdownMenuShortcut>⌘2</DropdownMenuShortcut>
                </DropdownMenuItem>
                <DropdownMenuItem onClick={designOnly}>
                  Size <DropdownMenuShortcut>⌘3</DropdownMenuShortcut>
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>

          <Dialog>
            <DialogTrigger render={<Button variant="outline" />}>
              <ArchiveRestore /> Restore dialog
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Restore this file?</DialogTitle>
                <DialogDescription>
                  It moves back to your archive and will no longer be purged automatically.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>Cancel</DialogClose>
                <DialogClose render={<Button />} onClick={designOnly}>
                  Restore
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Dialog>
            <DialogTrigger render={<Button variant="destructive" />}>
              <Trash2 /> Purge dialog
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Permanently delete?</DialogTitle>
                <DialogDescription>
                  The file and its history are removed for good. This cannot be undone.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>Keep it</DialogClose>
                <DialogClose render={<Button variant="destructive" />} onClick={designOnly}>
                  Delete permanently
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </Section>

      <Section
        title="Toasts"
        description="Brief, non-blocking feedback. Errors include what to do next."
      >
        <div className="flex flex-wrap gap-3">
          <Button
            variant="outline"
            onClick={() =>
              toast.success("File archived", { description: "Apartment lease 2021.pdf is safe." })
            }
          >
            Success
          </Button>
          <Button variant="outline" onClick={() => toast.info("Restored to your archive")}>
            Info
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              toast.warning("Duplicate detected", {
                description: "You already archived this file.",
              })
            }
          >
            Warning
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              toast.error("Upload failed", { description: "The file exceeds the 250 MB limit." })
            }
          >
            Error
          </Button>
        </div>
      </Section>

      <Section
        title="Cards"
        description="White surfaces with a hairline ring; elevation only on interaction."
      >
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {SAMPLE_FILES.map((file) => (
            <SampleFileCard key={file.id} file={file} />
          ))}
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Card with header</CardTitle>
            <CardDescription>
              Title, description and content follow the same spacing scale.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Text tone="muted">Content area.</Text>
          </CardContent>
        </Card>
      </Section>

      <Section title="Badges" description="Status and metadata labels.">
        <div className="flex flex-wrap gap-2">
          <Badge>Default</Badge>
          <Badge variant="accent">Accent</Badge>
          <Badge variant="secondary">Secondary</Badge>
          <Badge variant="outline">Outline</Badge>
          <Badge variant="success">Archived</Badge>
          <Badge variant="warning">Discarded</Badge>
          <Badge variant="info">Processing</Badge>
          <Badge variant="destructive">Failed</Badge>
        </div>
      </Section>

      <Section title="Loading skeletons" description="Match the shape of the content they replace.">
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardContent>
              <ListSkeleton rows={4} />
            </CardContent>
          </Card>
          <CardSkeleton />
        </div>
      </Section>

      <Section title="Empty states" description="Say what belongs here and why it is empty.">
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <EmptyState
              icon={Archive}
              title="Your archive is empty"
              description="Files you archive will be preserved, searchable and restorable."
              action={<Button onClick={designOnly}>Example action</Button>}
            />
          </Card>
          <Card>
            <EmptyState
              size="compact"
              icon={SearchX}
              title="No matches"
              description="Try fewer words or remove a filter."
            />
          </Card>
        </div>
      </Section>
    </main>
  );
}
