package pl.starchasers.furiouscraft;

import java.util.Map;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.config.Configuration;
import pl.starchasers.furiouscraft.asm.Transformer;

@TransformerExclusions({"pl.starchasers.furiouscraft"})
public class FuriousCraft extends DummyModContainer implements IFMLLoadingPlugin {
	public static Configuration config;
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

	@Override
	public boolean registerBus(EventBus bus, LoadController controller)
	{
		this.eventBus = bus;
		this.controller = controller;
		eventBus.register(this);
		return true;
	}

	@Subscribe
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getModConfigurationDirectory());
	}

	@NetworkCheckHandler
	public boolean checkNetwork(Map<String,String> mods, Side side) {
		return true;
	}
}
