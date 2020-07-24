package knightminer.inspirations.tools;

import knightminer.inspirations.common.ClientProxy;
import knightminer.inspirations.tools.client.RedstoneArrowRenderer;
import knightminer.inspirations.tools.item.WaypointCompassItem;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ToolsClientProxy extends ClientProxy {

	@SubscribeEvent
	public void clientSetup(FMLClientSetupEvent event) {
		RenderingRegistry.registerEntityRenderingHandler(InspirationsTools.entRSArrow, RedstoneArrowRenderer::new);
	}

	@SubscribeEvent
	public void registerItemColors(ColorHandlerEvent.Item event) {
		ItemColors itemColors = event.getItemColors();

		// Dyed waypoint compasses. This implements IItemColor itself.
		for(WaypointCompassItem compass : InspirationsTools.waypointCompasses.values()) {
			registerItemColors(itemColors, compass::getColor, compass);
		}
	}
}
