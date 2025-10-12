import type { JSX } from 'solid-js';
import { createSignal, Show, For } from 'solid-js';

export interface DropdownEntry {
	visible?: boolean
	value: JSX.Element
	//action: () => Promise<void>
}

export function Dropdown(props: { children: JSX.Element[], entries: DropdownEntry[], classes: string, dropdownClasses?: string, entryClasses?: string[], left?: boolean }) {
	const left = props.left ?? false
	const dropdownClasses = props.dropdownClasses ?? []
	const entryClasses = props.entryClasses ?? []
	const [dropdownVisible, setDropdownVisible] = createSignal(false);

	function toggleDropdown() {
		if (dropdownVisible()) {
				setDropdownVisible(false);
		} else {
				for (let entry of props.entries) {
						if (entry.visible === undefined || entry.visible) {
								setDropdownVisible(true);
								return;
						}
				}
				setDropdownVisible(false);
		}
	}

	return (
		<div class="relative inline-block" onfocusout={({ relatedTarget, currentTarget }) => {
				if (relatedTarget instanceof HTMLElement && currentTarget.contains(relatedTarget)) return
				setDropdownVisible(false)
		}}>
			<button onclick={toggleDropdown} class={"inline-flex w-full justify-center cursor-pointer "+props.classes}>
				{props.children}
			</button>
			<Show when={dropdownVisible()}>
				<div class={
					["overflow-auto", "shadow-sm", "absolute", "z-10", "mt-2", "rounded-md", "bg-white", "focus:outline-hidden", "w-56"].concat(dropdownClasses).concat(left ? ["left-0", "origin-top-left"] : ["right-0", "origin-top-right"]).join(" ")
				} role="menu" aria-orientation="vertical" aria-labelledby="menu-button" tabindex="-1">
					<div class="py-1" role="none">
						<For each={props.entries}>{(entry) =>
							<Show when={entry.visible === undefined || entry.visible}>
								<button onclick={async () => {
										setDropdownVisible(false)
										//await entry.action()
								}} class={
										["block", "px-4", "text-sm", "hover:bg-slate-150", "cursor-pointer", "w-full", "text-left", "py-2"].concat(entryClasses).join(" ")
								} role="menuitem">{entry.value}</button>
							</Show>}
						</For>
					</div>
				</div>
			</Show>
		</div>
	)
}

export interface AppEntry {
	dropdownValue: JSX.Element
	value: JSX.Element
}