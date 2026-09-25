package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.model.*;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingMatcherTest {
    ItemResolver resolver;
    Material wood, stone;
    @BeforeEach void setup() {
        resolver=mock(ItemResolver.class); wood=mock(Material.class);stone=mock(Material.class);
        when(resolver.newMatchContext()).thenReturn((item,spec)->item.getType()==spec.getMaterial());
    }
    ItemSpec spec(Material material,int amount) {return new ItemSpec(ItemKind.VANILLA,material,null,null,amount,MatchMode.TYPE,null);}
    ItemStack item(Material material,int amount) {
        ItemStack item=mock(ItemStack.class);when(item.getType()).thenReturn(material);when(item.getAmount()).thenReturn(amount);return item;
    }
    MdvRecipe shaped(String id,List<String> shape,Map<Character,ItemSpec> ingredients) {
        MdvRecipe r=mock(MdvRecipe.class);when(r.getId()).thenReturn(id);when(r.getStation()).thenReturn(StationType.CRAFTING_TABLE);
        when(r.getType()).thenReturn(RecipeType.SHAPED);when(r.getShape()).thenReturn(shape);when(r.getShapedIngredients()).thenReturn(ingredients);return r;
    }
    MdvRecipe shapeless(Map<String,ItemSpec> ingredients) {
        MdvRecipe r=mock(MdvRecipe.class);when(r.getStation()).thenReturn(StationType.CRAFTING_TABLE);
        when(r.getType()).thenReturn(RecipeType.SHAPELESS);when(r.getShapelessIngredients()).thenReturn(ingredients);return r;
    }
    @Test void cargamentoAcceptsNineLogsButRejectsMissingOrWrongIngredients() {
        var recipe=shaped("cargamento_madera_roble",List.of("AAA","AAA","AAA"),Map.of('A',spec(wood,1)));
        var matcher=new CraftingMatcher(List.of(recipe),resolver);
        ItemStack[] grid=new ItemStack[9]; Arrays.fill(grid,item(wood,64));
        assertSame(recipe,matcher.find(grid).orElseThrow());
        grid[8]=null; assertTrue(matcher.find(grid).isEmpty());
        grid[8]=item(stone,64); assertTrue(matcher.find(grid).isEmpty());
    }
    @Test void shapeMaskRejectsWrongPositionsBeforeComparingAndChecksAmounts() {
        var recipe=shaped("amatista",List.of("   ","AAA","AAA"),Map.of('A',spec(wood,2)));
        AtomicInteger comparisons=new AtomicInteger();
        when(resolver.newMatchContext()).thenReturn((item,spec)->{comparisons.incrementAndGet();return true;});
        var matcher=new CraftingMatcher(List.of(recipe),resolver);
        ItemStack[] grid=new ItemStack[9];Arrays.fill(grid,0,6,item(wood,64));
        assertTrue(matcher.find(grid).isEmpty());assertEquals(0,comparisons.get());
        Arrays.fill(grid,null);Arrays.fill(grid,3,9,item(wood,1));
        assertTrue(matcher.find(grid).isEmpty());
        Arrays.fill(grid,3,9,item(wood,2));assertSame(recipe,matcher.find(grid).orElseThrow());
    }
    @Test void inventoryTwoByTwoAndRegistrationPriorityArePreserved() {
        var first=shaped("first",List.of("AA","AA"),Map.of('A',spec(wood,1)));
        var second=shapeless(Map.of("A",spec(wood,4)));
        ItemStack[] grid=new ItemStack[4];Arrays.fill(grid,item(wood,1));
        assertSame(first,new CraftingMatcher(List.of(first,second),resolver).find(grid).orElseThrow());
        assertSame(second,new CraftingMatcher(List.of(second,first),resolver).find(grid).orElseThrow());
    }
    @Test void shapelessOverlappingChoicesCanReassignEarlierMatches() {
        ItemSpec broad=spec(wood,1), narrow=spec(stone,1);
        ItemStack a=item(wood,1), b=item(stone,1);
        when(resolver.newMatchContext()).thenReturn((item,spec)->spec==broad || item==a);
        var ingredients=new LinkedHashMap<String,ItemSpec>();ingredients.put("broad",broad);ingredients.put("narrow",narrow);
        var recipe=shapeless(ingredients);
        assertSame(recipe,new CraftingMatcher(List.of(recipe),resolver).find(new ItemStack[]{a,b,null,null}).orElseThrow());
    }
    @Test void shapelessWorstCaseChecksAtMost81PairsInsteadOfFactorialSearch() {
        var recipe=shapeless(Map.of("A",spec(wood,8),"B",spec(stone,1)));
        AtomicInteger comparisons=new AtomicInteger();
        when(resolver.newMatchContext()).thenReturn((item,spec)->{comparisons.incrementAndGet();return spec.getMaterial()==wood;});
        ItemStack[] grid=new ItemStack[9];Arrays.fill(grid,item(wood,64));
        assertTrue(new CraftingMatcher(List.of(recipe),resolver).find(grid).isEmpty());
        assertEquals(81,comparisons.get());
    }
    @Test void impossibleCountsAndEmptyMatricesDoNotMatch() {
        var recipe=shapeless(Map.of("A",spec(wood,Integer.MAX_VALUE)));
        var matcher=new CraftingMatcher(List.of(recipe),resolver);
        ItemStack[] grid=new ItemStack[9];Arrays.fill(grid,item(wood,64));
        assertTrue(matcher.find(grid).isEmpty());assertTrue(matcher.find(null).isEmpty());
        assertTrue(matcher.find(new ItemStack[4]).isEmpty());
    }
    @Test void largeRegistrySkipsRecipesWithOtherCountsWithoutComparing() {
        List<MdvRecipe> recipes=new ArrayList<>();
        for(int i=0;i<2000;i++) recipes.add(shaped("other"+i,List.of("A"),Map.of('A',spec(stone,1))));
        var woodRecipe=shaped("wood",List.of("AAA","AAA","AAA"),Map.of('A',spec(wood,1)));recipes.add(woodRecipe);
        AtomicInteger calls=new AtomicInteger();
        when(resolver.newMatchContext()).thenReturn((item,spec)->{calls.incrementAndGet();return item.getType()==spec.getMaterial();});
        var matcher=new CraftingMatcher(recipes,resolver);
        ItemStack[] grid=new ItemStack[9];Arrays.fill(grid,item(wood,64));
        assertSame(woodRecipe,matcher.find(grid).orElseThrow());assertEquals(9,calls.get());
    }
}
