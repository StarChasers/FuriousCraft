package pl.starchasers.furiouscraft;

import java.lang.reflect.Method;
import java.util.Map;

import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;
import cpw.mods.fml.relauncher.Side;

@TransformerExclusions({"pl.starchasers.furiouscraft"})
public class FuriousCraft extends DummyModContainer implements IFMLLoadingPlugin {
	private static final ModMetadata md = new ModMetadata();
	private EventBus eventBus;
	private LoadController controller;
	
	static{
		md.modId = "FuriousCraft";
		md.name = "FuriousCraft";
	}
	
	public FuriousCraft() {
		super(md);
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[]{Transformer.class.getName()};
	}

	@Override
	public String getModContainerClass() {
		return getClass().getName();
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public Object getMod() {
		return this;
	}

	@Override
	public void injectData(Map<String, Object> data) {

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

	@Subscribe
	public void load(FMLInitializationEvent event) {
		System.out.println("Registering PTU!");
		FMLCommonHandler.instance().bus().register(new FuriousCraftPartialTileUnloader());
	}

	@Override
	public boolean registerBus(EventBus bus, LoadController controller)
	{
		this.eventBus = bus;
		this.controller = controller;
		eventBus.register(this);
		return true;
	}

	@NetworkCheckHandler
	public boolean checkNetwork(Map<String,String> mods, Side side) {
		return true;
	}
}
