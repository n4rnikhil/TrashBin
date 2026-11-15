import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { MAX_TAGS, TagEditor } from "./tag-editor";

function Harness({ initial = [] as string[] }) {
  const [tags, setTags] = useState(initial);
  return (
    <>
      <TagEditor value={tags} onChange={setTags} suggestions={["Taxes"]} />
      <output data-testid="value">{tags.join("|")}</output>
    </>
  );
}

const value = () => screen.getByTestId("value").textContent;

describe("TagEditor", () => {
  it("adds tags with Enter or comma, trimming and collapsing whitespace", async () => {
    render(<Harness />);
    const input = screen.getByLabelText("Add a tag");

    await userEvent.type(input, "  Tax   Returns {Enter}2019,");

    expect(value()).toBe("Tax Returns|2019");
  });

  it("ignores case-insensitive duplicates", async () => {
    render(<Harness initial={["Work"]} />);

    await userEvent.type(screen.getByLabelText("Add a tag"), "work{Enter}");

    expect(value()).toBe("Work");
  });

  it("removes tags with their button or Backspace on an empty field", async () => {
    render(<Harness initial={["a", "b", "c"]} />);

    await userEvent.click(screen.getByRole("button", { name: "Remove tag b" }));
    expect(value()).toBe("a|c");

    await userEvent.type(screen.getByLabelText("Add a tag"), "{Backspace}");
    expect(value()).toBe("a");
  });

  it("explains instead of exceeding the limits", async () => {
    render(<Harness initial={Array.from({ length: MAX_TAGS - 1 }, (_, i) => `t${i}`)} />);
    const input = screen.getByLabelText("Add a tag");

    await userEvent.type(input, `${"x".repeat(51)}{Enter}`);
    expect(screen.getByText(/at most 50 characters/)).toBeInTheDocument();

    await userEvent.clear(input);
    await userEvent.type(input, "last{Enter}");
    expect(value()?.split("|")).toHaveLength(MAX_TAGS);
    expect(input).toBeDisabled();
  });
});
