package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.model.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ItemResolverPerformanceTest {
    MMOItemsHook hook;
    ItemResolver resolver;
    Material material;
    AtomicLong clock;
    @BeforeEach void setup() {
        hook=mock(MMOItemsHook.class); material=mock(Material.class);
        clock=new AtomicLong(); resolver=new ItemResolver(hook,clock::get);
    }
    ItemSpec spec(String id, MatchMode mode, int amount) {
        return new ItemSpec(ItemKind.MMOITEMS,null,"MATERIAL",id,amount,mode,null);
    }
    ItemStack item() {
        ItemStack item=mock(ItemStack.class);
        when(item.getType()).thenReturn(material); when(item.getAmount()).thenReturn(64);
        return item;
    }
    void identity(ItemStack item,String id) {
        when(hook.readIdentity(item)).thenReturn(Optional.of(new MMOItemsHook.MmoIdentity("MATERIAL",id)));
    }
    @Test void vanillaLogsRejectAllCustomCandidatesWithoutBuildingItems() {
        ItemStack logs=item(); when(hook.readIdentity(logs)).thenReturn(Optional.empty());
        var match=resolver.newMatchContext();
        for(int i=0;i<1000;i++) assertFalse(match.test(logs,spec("CUSTOM_"+i,MatchMode.SIMILAR,1)));
        verify(hook,never()).buildItem(anyString(),anyString());
        verify(hook,times(1)).readIdentity(logs);
    }
    @Test void sameIdentityStillRequiresSimilarMetadataAndWrongIdIsRejected() {
        ItemStack actual=item(), template=item(), reference=item(); identity(actual,"AMATISTA");
        when(hook.buildItem("MATERIAL","AMATISTA")).thenReturn(template);
        when(template.clone()).thenReturn(reference);
        when(actual.isSimilar(reference)).thenReturn(false);
        assertFalse(resolver.matches(actual,spec("AMATISTA",MatchMode.SIMILAR,1)));
        when(actual.isSimilar(reference)).thenReturn(true);
        assertTrue(resolver.matches(actual,spec("AMATISTA",MatchMode.SIMILAR,1)));
        assertFalse(resolver.matches(actual,spec("HUESO",MatchMode.SIMILAR,1)));
        verify(hook,times(1)).buildItem("MATERIAL","AMATISTA");
        verify(hook,never()).buildItem("MATERIAL","HUESO");
    }
    @Test void bulkMatchingSharesOneReferenceAcrossSymbolsAndStackAmounts() {
        ItemStack actual=item(), template=item(), reference=item(); identity(actual,"FRAGMENTO");
        when(hook.buildItem("MATERIAL","FRAGMENTO")).thenReturn(template);
        when(template.clone()).thenReturn(reference); when(actual.isSimilar(reference)).thenReturn(true);
        for(int craft=0;craft<1000;craft++) {
            var match=resolver.newMatchContext();
            for(int symbol=0;symbol<9;symbol++) assertTrue(match.test(actual,spec("FRAGMENTO",MatchMode.SIMILAR,1+symbol)));
        }
        verify(hook,times(1)).buildItem("MATERIAL","FRAGMENTO");
        System.out.println("MATCH LOAD: 9000 strict comparisons; MMOItems builds = 1 (previous path: 9000).");
    }
    @Test void explicitMmoIdModeDoesNotBuildReference() {
        ItemStack actual=item(); identity(actual,"FRAGMENTO");
        assertTrue(resolver.matches(actual,spec("FRAGMENTO",MatchMode.MMO_ID,1)));
        verify(hook,never()).buildItem(anyString(),anyString());
    }
    @Test void previewIsClonedAndActualResultsRemainFresh() {
        ItemStack template=item(), reference=item(), preview1=item(),preview2=item();
        when(hook.buildItem("MATERIAL","CARGAMENTOMADERA")).thenReturn(template);
        when(template.clone()).thenReturn(reference);
        when(reference.clone()).thenReturn(preview1,preview2);
        ItemSpec result=spec("CARGAMENTOMADERA",MatchMode.SIMILAR,1);
        assertSame(preview1,resolver.buildPreview(result));
        assertSame(preview2,resolver.buildPreview(result));
        resolver.buildItem(result); resolver.buildItem(result);
        verify(hook,times(3)).buildItem("MATERIAL","CARGAMENTOMADERA");
    }
    @Test void referencesExpireAndReloadClearsThemIncludingMissingItems() {
        ItemSpec result=spec("MISSING",MatchMode.SIMILAR,1);
        assertNull(resolver.buildPreview(result)); assertNull(resolver.buildPreview(result));
        verify(hook,times(1)).buildItem("MATERIAL","MISSING");
        clock.set(60_000_000_001L); resolver.buildPreview(result);
        verify(hook,times(2)).buildItem("MATERIAL","MISSING");
        resolver.clearCaches(); resolver.buildPreview(result);
        verify(hook,times(3)).buildItem("MATERIAL","MISSING");
    }
    @Test void referencesAreBoundedTo1024Entries() {
        for(int i=0;i<1025;i++) resolver.buildPreview(spec("ID_"+i,MatchMode.SIMILAR,1));
        resolver.buildPreview(spec("ID_0",MatchMode.SIMILAR,1));
        verify(hook,times(2)).buildItem("MATERIAL","ID_0");
    }
    @Test void yamlStillDefaultsToSimilar() throws Exception {
        var yaml=new YamlConfiguration();
        yaml.loadFromString("kind: MMOITEMS\ntype: MATERIAL\nid: FRAGMENTOAMATISTAENCANTADA\namount: 1\n");
        assertEquals(MatchMode.SIMILAR,resolver.fromConfig(yaml).getMatchMode());
    }
    @Test void exactComparisonNormalizesCopyWithoutChangingReference() {
        ItemStack actual=item(), template=item(), reference=item(), comparison=item(); identity(actual,"EXACT");
        when(hook.buildItem("MATERIAL","EXACT")).thenReturn(template);
        when(template.clone()).thenReturn(reference); when(reference.clone()).thenReturn(comparison);
        assertFalse(resolver.matches(actual,spec("EXACT",MatchMode.EXACT,1)));
        verify(comparison).setAmount(64);
        verify(reference,never()).setAmount(64);
        verify(actual,never()).setAmount(anyInt());
        verify(actual,never()).isSimilar(any());
    }
}
