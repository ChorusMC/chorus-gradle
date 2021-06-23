import net.fabricmc.api.ModInitializer;
import net.fabricmc.yarn.constants.CatTypes;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// Just enough to make sure it can compile against the flags
		System.out.println(CatTypes.TABBY);
	}
}