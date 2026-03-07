package net.runelite.client.plugins.barace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Attack
{
	BLOCK_INVENTORY("Block inventory"),
	JUNK_MENU_OPTIONS("Junk menu options"),
	BLOCK_LEFT_CLICK_INVENTORY("Block left-click inventory"),
	HIDE_RUNNERS("Hide runners");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
