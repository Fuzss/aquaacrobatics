package com.fuzs.aquaacrobatics.proxy;

import com.fuzs.aquaacrobatics.client.handler.AirMeterHandler;
import com.fuzs.aquaacrobatics.client.handler.FogHandler;
import com.fuzs.aquaacrobatics.integration.IntegrationManager;
import com.fuzs.aquaacrobatics.integration.artemislib.ArtemisLibIntegration;
import com.fuzs.aquaacrobatics.integration.mobends.MoBendsIntegration;
import net.minecraftforge.common.MinecraftForge;

@SuppressWarnings("unused")
public class ClientProxy extends CommonProxy {

    @Override
    public void onPreInit() {

        super.onPreInit();
        MinecraftForge.EVENT_BUS.register(new AirMeterHandler());
        MinecraftForge.EVENT_BUS.register(new FogHandler());
    }

    @Override
    public void onPostInit() {

        super.onPostInit();
        if (IntegrationManager.isMoBendsEnabled()) {

            MoBendsIntegration.register();
        }

        if (IntegrationManager.isArtemisLibEnabled()) {

            ArtemisLibIntegration.register();
        }
    }

}
