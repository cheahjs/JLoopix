package me.jscheah.jloopix.client;

import me.jscheah.jloopix.SphinxPacker;
import me.jscheah.jloopix.nodes.LoopixNode;
import me.jscheah.sphinx.HexUtils;
import me.jscheah.sphinx.SphinxPacket;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.params.GroupECC;
import me.jscheah.sphinx.params.SphinxParams;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import static me.jscheah.jloopix.Core.packValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCoreTest {
    private SphinxPacker packer;
    @Spy private GroupECC group = new GroupECC();
    private ClientCore core;
    private ECPoint publicKey;
    private BigInteger privateKey;

    @BeforeEach
    void setUp() {
        // Mock random
        MockitoAnnotations.initMocks(this);
        when(group.generateSecret()).thenReturn(new BigInteger("1"));
        try {
            // Make sure padding is not random
            SphinxParams params = Mockito.spy(new SphinxParams(group, 1024, 1024));
            doNothing().when(params).randomBytes(any(byte[].class));
            packer = new SphinxPacker(params, 0);
            privateKey = new BigInteger("1");
            publicKey = group.Generator;
            core = new ClientCore(0, packer, "client_1", (short) 1, "client_1", publicKey, privateKey);
        } catch (Exception e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testRealPacket() {
        try {
            SphinxPacket realMessage = createRealPacket();
            byte[] packed = packValue(realMessage.toValue());
            // check packet length
            assertEquals(packed.length, 2079);
            // compare Python
            assertEquals("9293c7220292cd02c9bd02b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21c503e07780ed79536416ec6639363c30134d0229662ed126ae40a47d623a5bdaed58d40a82d31b617283514656e81ac06755b7c1693fd963ddbf85cf05673ae494707b064dd53495d4dbeaf7e10804f3501fdc420967d4d768eb88dbefcd83146b3bd5bf75ab521b6e002246bb08c6478eafc4b42730b82a744a67174da318f3919c747c4f6381e8a1597041a53628fc98b5eedf2f28187f23d4110eab49519c0526f650fcf1855c1390b982797e29b0f82252a28bf3c57cac3ccc289ae90d1f65886ba22ef4b81aea242914dea8573150556ef47054f1093fa760d35f7f3d9074d669e10478d663a084219898530fe90dc6a5a2f488b76cb8fdbaaad27c6dd6741ed094bb220eb9032e3fdba01f3b7aba0ae10bce08429eef20b0147b3c2be380e879ae53ac90e7f85fb56761eaa8ccb3e9d145211f28e2cddca54eeab8265c2e06e8453ba0bd600fd98f31d4e1f79700d143600e50ecd21b3d0f9c946d3855358035d68de1404f580c18fa55763fa4a47b124fa1cacab5f4697c0859211e9d1365c9ece1656e03f0540aafef856ef9bf872c7231d8b26e3f809bd393382e8a2d0aae8538f8f467f007d278a94d0ef051de7d59caa4b0c33651d2bf01743036e9e8ef297594c79ef93c3ece5bf9c7543641519195676331d0b91b6cf2938267d61d5ce1c336ebe63b81882c628e6e2f716fb27c971e92a9ab733a7d1490944324f16b0428f5def617e794a4702e4e9e72692812593ac05808e28c8121e469affadcdcd25445102a2df42a72d6286bcb9f269defcadaaca4f7eb2182daec8e3202a1f0e96c93a7855a59acc446900caaa368d22bd315a43f21865b9eb4c5ea18fc65ae41ccf44ba3c2e27bc677cd960a7ac6121c95ef8a8634b615e183cb9ce092ec17d58a739ba3a324c0b4eb8c6730d89f67fc81d5b464343c985616b627cfcbd71cd92e5ea57dcc15a410b1bc7292368200116fa8de8a2b745128459bec7ee6143bb757b6269faa2fc8aeb56ea73d3235c2f9d367d165bfeff01617c5056489036e6d6c0a19eb36e5a8d2b8b659a0be3191efcb6a89d77aee62d59b52e9354381da6cc438568dbd9d5428b2cc4e1e4cff241b7d412e69242ee70b40e6ac2c848f3d1af0473aa1bd7c537a451ac0bcf4cee82a155d0869149b54fe92a1849bda80e2d263cf9469568387978b3a5ee05fb8113d8d0daf1c332d610ce045210780c3abe3781b9948ba125d01a446b3472387a8eea80e6f0bb91d34ee802cebcd8d6a6e6f6e949ae7bc302ce517f6a570f25a61f9162bb698e311ad4965a330874aeee85b0f8b6bd77a3747a22836cda11e35e9381e5639277a7cb9fe25b65c7faa92cc625d72e4a022620842e3bb21c69bd64abf6687ddf7f23c9148df8d1f315c0be2c410e172ff85fa54c427afd06d1f959257e4c50400f487f899fe0174eaf0ea8a52abdeb554f75a1f965d68d308d585fb05611b5441a3e3270d986d5af3fe29caa3b3b4443f8fa32797470a6612e31d30f84fd0da7d450a1a22f5e8df4f6ba01e2e8e1707c0cadbe8c4ee2f813656829351d1aeaee02854a1da00729fd7395c2c0d2cd360a97e66d5da82adb6d4d11522fd9cc446ab9aad755848d3ba85a3072437c8c5cdfabd8d1205c66b57bc3a6a26f491e4ad2efb7cce3552eae36cb67eb061a5e833a1f2c59de038ec01b370d5e26bb20206b9b18409eed5d0a95da51f6645f01e4caf629aaa945375db1d042976bf1f1d8c9637c6c894e05133c89374ca58dc5ae046f16a901e9c159728107db9eb5ff098d58e2a6c7d51b0205093e4417e3cab0db9564a539d523ecf789ce956eb0a7775c7fdd138e93b30626efea7af3c8b916b15abbc70c60f2f69b591254f6a9aa458afb71451be466c5594f9120560fca7d6af6333c09ab61ede69a8e7c52d3461f87dbc29f7046326ca5f4acf84606baf89464713dfed886a85318fa0296a9ad3b015bbb614a9d2065c018b6828fd605814125d031edcc9506ff0922cb6e11c219e41d931ec065bc58eaea651d48a7729c07105d1035bf8c307a961ab97dfb9ff701a004f33fb0b57be052d7d85029c2f3f6186d4630f88c7157096316da9d9ee8cd562edc87a367bbff729d271856a9352d643e7c612c008be4c000f781d2baa3805093babbd03b8532621da6d0f9484467398f3bda98852c65f1649be69f37f67309c00e59eca88e4665f8747cb454440208e2154aef823b6ae6fcf064b8db711584bbe31f6fda03b48e5aec9e118da2495b9d610bd7288780717fc0738f09b7b7607e4232663fefad94711c0092e6b00fb6a1a4c2b74d26e5f952bf896ae310da0100ae41701adc3094dd69e678c0de2f64d748fba238ee688ce0830e9b733e8796264fe21e2511338b19238d13cfea07539104fb679468e3a4fbcc99aa343bcae5cf624b5db86426f134563395f6964833fbd553e36a931c63b39cdb217cfe3c0fcdb23cb3f7978531b78b78870f00383420c1deeed09da9c4b661c851c0d27959b0058d54b36c2b20dfae082b9aee618fc7b55b782b1de62e0c2c6df68b43638fa063e4fe7612031c2f3be9d07855c054506a9990a424f2afc5e261af3e6afc926307560ce07161812e122288b9027db64933a44f897a0a91ea2db23f13c85e9c99918293ac0afee11584bc6f8cf47ca187f4788e940ecbbe6204c5fcd2ea690d509e8150f460be3496734f4a8986e74422f1d920ff85cb1c734fcfb6212f2a4f96a06f9c182242612125b7a20280dcc1ebb9fbea137b525232c2f1a2ed53be667316debd0b423d8d0a4d1ef75c056be2a64cee1afbaa6fb1721ac403edbbac723fbe7cdcc398214ea4cf4dcbf4c481f4063d4031056ea2a19561eb988b5f4a4",
                    HexUtils.hexlify(packed));
        } catch (CryptoException | IOException | SphinxException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    private SphinxPacket createRealPacket() throws CryptoException, IOException, SphinxException {
        // Create path
        List<LoopixNode> path = new LinkedList<>();
        path.add(new LoopixNode("provider_1", (short) 1, "provider_1", publicKey));
        path.add(new LoopixNode("mix_1", (short) 1, "mix_1", publicKey));
        path.add(new LoopixNode("mix_2", (short) 1, "mix_2", publicKey));
        path.add(new LoopixNode("mix_3", (short) 1, "mix_3", publicKey));
        path.add(new LoopixNode("provider_2", (short) 1, "provider_2", publicKey));
        return core.createRealMessage(new LoopixNode("client_2", (short) 1, "client_2", publicKey), path, "TEST".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testLoopPacket() {
        try {
            // Create path
            List<LoopixNode> path = new LinkedList<>();
            path.add(new LoopixNode("provider_1", (short) 1, "provider_1", publicKey));
            path.add(new LoopixNode("mix_1", (short) 1, "mix_1", publicKey));
            path.add(new LoopixNode("mix_2", (short) 1, "mix_2", publicKey));
            path.add(new LoopixNode("mix_3", (short) 1, "mix_3", publicKey));
            path.add(new LoopixNode("provider_2", (short) 1, "provider_2", publicKey));
            SphinxPacket loopMessage = core.createLoopMessage(path);
            byte[] packed = packValue(loopMessage.toValue());
            // check packet length
            assertEquals(packed.length, 2079);
            // compare Python
            assertEquals("9293c7220292cd02c9bd02b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21c503e07780ed79536416ec6639363c30134d0229662ed126ae40a47d623a5bdaed58d40a82d31b617283514656e81ac06755b7c1693fd963ddbf85cf05673ae494707b064dd53495d4dbeaf7e10804f3501fdc420967d4d768eb88dbefcd83146b3bd5bf75ab521b6e002246bb08c6478eafc4b42730b82a744a67174da318f3919c747c4f6381e8a1597041a53628fc98b5eedf2f28187f23d4110eab49519c0526f650fcf1855c1390b982797e29b0f82252a28bf3c57cac3ccc289ae90d1f65886ba22ef4b81aea242914dea8573150556ef47054f1093fa760d35f7f3d9074d669e10478d663a084219898530fe90dc6a5a2f488b76cb8fdbaaad27c6dd6741ed094bb220eb9032e3fdba01f3b7aba0ae10bce08429eef20b0147b3c2be380e879ae53ac90e7f85fb56761eaa8ccb3e9d145211f28e2cddca54eeab8265c2e06e8453ba0bd600fd98f31d4e1f79700d143600e50ecd21b3d0f9c946d3855358035d68de1404f580c18fa55763fa4a47b124fa1cacab5f4697c0859211e9d1365c9ece1656e03f0540aafef856ef9bf872c7231d8b26e3f809bd393382e8a2d0aae8538f8f467f007d278a94d0ef051de7d59caa4b0c33651d2bf01743036e9e8ef297594c79ef93c3ece5bf9c7543641519195676331d0b91b6cf2938267d61d5ce1c336ebe63b81882c628e6e2f716fb27c971e92a9ab733a7d1490944324f16b0428f5def617e794a4702e4e9e72692812593ac05808e28c8121e469affadcdcd25445102a2df42a72d6286bcb9f269defcadaaca4f7eb2182daec8e3202a1f0e96c93a7855a59acc446900caaa368d22bd315a43f21865b9eb4c5ea18fc65ae41ccf44ba3c2e27bc677cd960a7ac6121c95ef8a8634b615e183cb9ce092ec17d58a739ba3a324c0b4eb8c6730d89f67fc81d5b464343c985616b627cfcbd71cd92e5ea57dcc15a410b1bc7292368200116fa8de8a2b745128459bec7ee6143bb757b6269faa2fc8aeb56ea73d3235c2f9d367d165bfeff01617c5056489036e6d6c0a19eb36e5a8d2b8b659a0be3191efcb6a89d77aee62d59b52e9354381da6cc438568dbd9d5428b2cc4e1e4cff241b7d412e69242ee70b40e6ac2c848f3d1af0473aa1bd7c537a451ac0bcf4cee82a155d0869149b54fe92a1849bda80e2d263cf9469568387978b3a5ee05fb8113d8d0daf1c332d610ce045210780c3abe3781b9948ba125d01a446b3472387a8eea80e6f0bb91d34ee802cebcd8d6a6e6f6e949ae7bc302ce517f6a570f25a61f9162bb698e311ad4965a330874aeee85b0f8b6bd77a3747a22836cda11e35e9381e5639277a7cb9fe25b65c7faa92cc625d72e4a022620842e3bb21c69bd64abf6687ddf7f23c9148df8d1f315c0be2c410e172ff85fa54c427afd06d1f959257e4c50400a0c043b0ea49898f04293683cb7b7c943431985aa9753895422da1d043f879527c274d54594854d0b73b8278d72ad7449620b0c261bf46ed5d44ae6ae3f93ac3ea2ca47a9dca2e6dbc2b0a52617e5cc11d4525404d3614cb403c5b675de7293740c273f349bec1f0ebab28dfd261b0af74ded5d41906b0ee82c8b1eca149c0a31b8e2b171f32f067a5eb9782234cbe07996f27506f48420f172054d413cc218c172d42b650135a316dfe9152e4130b3b544ec464034131e10f334775cd9024f8ddf2f4dc945f61ae8f83c625a05e906277ef0416ec0ec5147803c4ecfe8ca44412c0a45aa63514c134334484c9d3124f8552ae4398b57982c6f225f0b010ebb6f6090ffc8a33eb818a13ec65d39cf93f440270c4eeec43676b7994aeacc1b1ed878109d04bc169691fba1d751df9877f303dfb6884a9fb61a6f9f249867a59e1bbd0861f22cb40014de3166d862713f7f507f536a82fbaffd13db505c71514108a1888f7609758c23bae80a3a2a7b96ec31c1fe325ce006b570e9caf3b9ed7ee2fed9ebea990afe2e69a790c91cb73e8e5238060a87dddd65251c9ccb0417e620f7dd858a2bfa14fae54826a5a03dac44276ded52be0ba53cf0c94397d095c3016f53820b970c48d9fffbcf4d0149e8dc5761c853670a27e6040765d14a23e141bf5e1f004ccbac6c0a3fe3bfd01d244fe22fa60980909af0a0f1fa5eab03061f3f21b18421e6b0e870e4025578bf4d737d756953ddf2788b290facc35b0f409ab08c5be7879e1a57f6603832cc3e1b58f1841c31095b22d316df5f1b988bda699a2968b97bde4eaa9407fe02c0760649d26547c80a425733deb741f032c1f83d2e4a1440e0a77857d6435258363a666bae4e0b32618fd8ad61340364af7e80cc4eccf4f93bdf82089c06f47cad1c680923487fdeaeffdc08a00ac9fdf4458adc770fb7d48b1f3b8f98016ec8a7196ae37129d37740a265443d08945ae05797ead6ad18198cd05fa8906531b718201e209770f310bf07030bac3f41609f33d6d10a2258305008c4a7bf577f55aa64709a14a5ae273ed70200b0e1f5f37d2b9dba90a0dfb81602aa09d20c7afde9232bd89c8865d8032c35da7749e407391ade58b9f63320352340730738bb71a566c8fd66b3cba767a6457561cd519acfaa8df74cced1b3256a2bdbbfc83deddfc1de592a3a1644fddb0c79994c1d11b3e06e0f2d26aeb286b093a19e04dc88d7be1dd9d944a7cc7e08fc792c506402e4acda990613943a78dd45b0fb2973d141c4f6b9e43206dac8815b2445f80573ee1e88702634b1be47d8178c90570e749b754d5c3b12d06ac15f6876fb54c437d242bac3d0562d60080b4048fcae24023643605159be1c7316e0641734f141acc6437966d0b1f443398bc78e62350d7477be0fb0519925ea068f6cd751c354ba2a0c8c5",
                    HexUtils.hexlify(packed));
        } catch (CryptoException | IOException | SphinxException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testDropPacket() {
        try {
            // Create path
            List<LoopixNode> path = new LinkedList<>();
            path.add(new LoopixNode("provider_1", (short) 1, "provider_1", publicKey));
            path.add(new LoopixNode("mix_1", (short) 1, "mix_1", publicKey));
            path.add(new LoopixNode("mix_2", (short) 1, "mix_2", publicKey));
            path.add(new LoopixNode("mix_3", (short) 1, "mix_3", publicKey));
            path.add(new LoopixNode("provider_2", (short) 1, "provider_2", publicKey));
            SphinxPacket dropMessage = core.createDropMessage(new LoopixNode("client_2", (short) 1, "client_2", publicKey), path);
            byte[] packed = packValue(dropMessage.toValue());
            // check packet length
            assertEquals(packed.length, 2079);
            // compare Python
            assertEquals("9293c7220292cd02c9bd02b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21c503e07780ed79536416ec6639363c30134d0229662ed126ae40a47d623a5bdaed58d40abe23573f378ab5974f822e501495f84e693fd963ddbf85cf05673ae494707b064dd53495d4dbeaf7e10804f3501fdc4209258cd0cf18b2047e627e8d244e7a40b8ab521b6e002246bb08c6478eafc4b42730b82a744a67174da318f3919c747c4f6391a1af22546c7fd52ea894524bb0486b187f23d4110eab49519c0526f650fcf1855c1390b882797e29b0f82252a28bf3c57cac3ccc289ae90d1f65886ba22ef4b81aea242914dea8573150556ef47054f1093fa760d35f7f3d9074d669e10478d663a084219898530fe90dc6a5a2f488b76cb8fdbaaad27c6dd6741ed094bb220eb9032e3fdba01f3b7aba0ae10bce08429eef20b0147b3c2be380e879ae53ac90e7f85fb56761eaa8ccb3e9d145211f28e2cddca54eeab8265c2e06e8453ba0bd600fd98f31d4e1f79700d143600e50ecd21b3d0f9c946d3855358035d68de1404f580c18fa55763fa4a47b124fa1cacab5f4697c0859211e9d1365c9ece1656e03f0540aafef856ef9bf872c7231d8b26e3f809bd393382e8a2d0aae8538f8f467f007d278a94d0ef051de7d59caa4b0c33651d2bf01743036e9e8ef297594c79ef93c3ece5bf9c7543641519195676331d0b91b6cf2938267d61d5ce1c336ebe63b81882c628e6e2f716fb27c971e92a9ab733a7d1490944324f16b0428f5def617e794a4702e4e9e72692812593ac05808e28c8121e469affadcdcd25445102a2df42a72d6286bcb9f269defcadaaca4f7eb2182daec8e3202a1f0e96c93a7855a59acc446900caaa368d22bd315a43f21865b9eb4c5ea18fc65ae41ccf44ba3c2e27bc677cd960a7ac6121c95ef8a8634b615e183cb9ce092ec17d58a739ba3a324c0b4eb8c6730d89f67fc81d5b464343c985616b627cfcbd71cd92e5ea57dcc15a410b1bc7292368200116fa8de8a2b745128459bec7ee6143bb757b6269faa2fc8aeb56ea73d3235c2f9d367d165bfeff01617c5056489036e6d6c0a19eb36e5a8d2b8b659a0be3191efcb6a89d77aee62d59b52e9354381da6cc438568dbd9d5428b2cc4e1e4cff241b7d412e69242ee70b40e6ac2c848f3d1af0473aa1bd7c537a451ac0bcf4cee82a155d0869149b54fe92a1849bda80e2d263cf9469568387978b3a5ee05fb8113d8d0daf1c332d610ce045210780c3abe3781b9948ba125d01a446b3472387a8eea80e6f0bb91d34ee802cebcd8d6a6e6f6e949ae7bc302ce517f6a570f25a61f9162bb698e311ad4965a330874aeee85b0f8b6bd77a3747a22836cda11e35e9381e5639277a7cb9fe25b65c7faa92cc625d72e4a022620842e3bb21c69bd64abf6687ddf7f23c9148df8d1f315c0be2c410f4d5c831fec383a02f492dc94a87cbcec5040015826d8915a8307e91239515e51a71be54a74da893273749cbc8b979c07ee0c5f022338fce38c76301850e96ee319c988ea9e0266328bf12129a4d7df900729d7d9badb0a1dd9f05550a59274ca27f831dfdc6e59509f46f82d16f2f106eefbb6abcbf152972459a81224ca025d8a2fca597dbca3e423bf1fda5a7c1c6859473007c379074ffb7c74fe71c75e25a4f1e0e3d7e2dce785d43c2fb30cb6d49ed8b871a10dd25713394563696bee3b0c287c85aa0b2d85d77072fa3c6a7fc1acc709cb39a90d8d0890d7e3db1f20fedad3b2df38ed5e46cc0df1ef0b3f6a1676194cb82efff2a5a116f57030a951430471d06d8f5f5e3fc569b596b3a38ed5f5420db0f58da2b2f8f3cc047a8cb9a4db9a3a6a8d2e1628fdb94a5f3585a68689b9d6a94e28ba35c25ad64a8bedd5f03aacc6460455a88779c0dab72784237ba755480a16fab615e76dc22ad7a0c14ad2037d8b86cb819999309cb61aa8d5effd1d046a9960f962d33dfa370ab5314929798dc95c24c3a9326b15eabc87125e3974ca9ad1962588e4255142233bfd810b8f386839c6ad1e567ed72fb02539b4a638a755655128b3e938e6cb5a63d41588eed0f766ac3c1d24bc0582c9af2d3ca1209b39cceb4cae997128a47b0da4f6fb62b2e1f0ee415ff73d2ddf68e0b749d90101c4232f6437f62fd1cee9a05666cef0d35af2aeb5d036f2a80d1473053ae7913240d29c3e7cf05d976f238d8dd7602960c5d69c7373dd35557dc9bbc3670f5b30e68fd7cf3344c8cdc2d50e462d1c97864e911e06679a56b575e43a58e12eaea85d950f2e5b2c8b7e59dc65f2de077a81717281b5184100fb81dc05df1724a4385a20ddf363f962dee4652ca86ac9eda20aaf6ddae8ea1a2807b4b35842d87aaaef0007a97a30532d18ed5b54c03dad3de01fa188394b4dd2692395a88ee867e3c398ca22d3cb0e25a8c1e44245209c264a962545a6a98ced64d2f4ba50e40b8714bede8f74161b8b5bb4457f3bf66397fd814266cda277661578c4a08872abdf8b88087f75b7101e557ae17f07e65bc73b99b0aca143391a2351aa7bf69f00f1501140648fd60a0867a85d40cf527664effb774020ac44222555aa05244cac5496efc129194913f44438a7ace12b756e935cf76ef239276edce55d42bc20918ab9c55fc759ddac4abe9c5eec5da46425c65c9b348b3f70cd050853c9489c041d8e51c9c33122c1bb348bf53d5a954c4d4375408d837c74ccb17699fa6a16dd9f562e9444fe7bda667ef8322677706563417d8507945da7badbd0ee28644da369ffecffd408838302bf111dfed8fa30c3e55fae7033f70911052da920b85180cfec3b7aadbd529c55c08f60535b8738f35bd1b1ac7b290c0525769f7744f0944120c9f12aa61f2da112eba84b40fef914b38e7a92e99e1a1465e0894eaf0efe3",
                    HexUtils.hexlify(packed));
        } catch (CryptoException | IOException | SphinxException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testProcessRealPacket() {
        try {
            SphinxPacket realMessage = createRealPacket();
            byte[] packed = packValue(realMessage.toValue());
            SphinxPacket decodedPacket = core.decodePacket(packed);
            byte[] decodedMessage = core.processPacket(decodedPacket);
            assertArrayEquals(decodedMessage, "TEST".getBytes(StandardCharsets.UTF_8));
        } catch (UnknownPacketException | CryptoException | IOException | SphinxException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testSelfDestinationCheck() {

    }
}