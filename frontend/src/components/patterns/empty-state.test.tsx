import { render, screen } from "@testing-library/react";
import { Archive } from "lucide-react";
import { describe, expect, it } from "vitest";
import { EmptyState } from "./empty-state";

describe("EmptyState", () => {
  it("renders a heading, description and optional action", () => {
    render(
      <EmptyState
        icon={Archive}
        title="Your archive is empty"
        description="Files will appear here."
        action={<button type="button">Do it</button>}
      />,
    );

    expect(screen.getByRole("heading", { name: "Your archive is empty" })).toBeInTheDocument();
    expect(screen.getByText("Files will appear here.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Do it" })).toBeInTheDocument();
  });

  it("omits the action area when no action is given", () => {
    render(<EmptyState icon={Archive} title="Empty" description="Nothing yet." />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
