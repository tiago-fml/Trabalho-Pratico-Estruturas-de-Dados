/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package json;

import exceptions.ElementNotFoundException;
import exceptions.InvalidDocumentException;
import exceptions.InvalidOperationException;
import exceptions.InvalidWeightValueException;
import exceptions.NullElementValueException;
import exceptions.RepeatedElementException;
import exceptions.VersionAlreadyExistException;
import graph.WeightedAdjMatrixDiGraph;
import interfaces.ICenario;
import interfaces.IDivisao;
import interfaces.IMissao;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import linkedListSentinela.UnorderedLinkedList;
import missoes.Alvo;
import missoes.Cenario;
import missoes.Divisao;
import missoes.Inimigo;
import missoes.Missao;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *Classe onde será feita a importação de uma missão em ficheiro json.
 */
public class JsonImporter {

    /**
     * Constructor for JsonImporter.
     */
    public JsonImporter() {
    }

    /**
     * Import JSON file.
     * @return IOrders
     */
    public static IMissao jsonImporter(String path) throws IOException, ParseException,
            FileNotFoundException, NullElementValueException, RepeatedElementException,
            ElementNotFoundException, InvalidWeightValueException, InvalidOperationException,
            VersionAlreadyExistException, InvalidDocumentException {
        IMissao missao = null;
        missao = importFile(path);
        return missao;
    }

    /**
     * Import JSON file.
     * @return IOrders
     */
    private static IMissao importFile(String path) throws FileNotFoundException, IOException,
            ParseException, NullElementValueException, RepeatedElementException,
            ElementNotFoundException, InvalidWeightValueException, InvalidOperationException,
            VersionAlreadyExistException, InvalidDocumentException {

        IMissao missao = null;

        try {
            JSONObject resultObject;
            JSONParser parser = new JSONParser();

            Reader reader = new FileReader(path);
            resultObject = (JSONObject) parser.parse(reader);

            String jCod = (String) resultObject.get("cod-missao");
            long jVersao = (long) resultObject.get("versao");

            JSONArray jEdificio = (JSONArray) resultObject.get("edificio");
            JSONArray jLigacoes = (JSONArray) resultObject.get("ligacoes");
            JSONArray jInimigos = (JSONArray) resultObject.get("inimigos");
            JSONArray jEntradasSaidas = (JSONArray) resultObject.get("entradas-saidas");
            JSONObject jAlvo = (JSONObject) resultObject.get("alvo");

            missao = new Missao(jCod);

            WeightedAdjMatrixDiGraph<IDivisao> edificio = new WeightedAdjMatrixDiGraph<>();

            //Importar inimigos para as divisões
            for (int i = 0; i < jEdificio.size(); i++) {
                IDivisao divisao = new Divisao(jEdificio.get(i).toString());               
                divisao=JsonImporter.importarDivisao(jInimigos, divisao);
                edificio.addVertex(divisao);
            }
            
            try{
            //Inserir ligacoes entre divisões
            for (int i = 0; i < jLigacoes.size(); i++) {

                JSONArray jLigacao = (JSONArray) jLigacoes.get(i);

                String jVertex1 = (String) jLigacao.get(0);
                IDivisao divisao1 = new Divisao(jVertex1);

                String jVertex2 = (String) jLigacao.get(1);
                IDivisao divisao2 = new Divisao(jVertex2);

                edificio.addEdge(divisao1, divisao2, edificio.getVertex(divisao2).getDano());
                edificio.addEdge(divisao2, divisao1, edificio.getVertex(divisao1).getDano());
            }
            }catch (ElementNotFoundException ex){
                throw new ElementNotFoundException("Ligações Inválidas.Divisão não existe no edificio!");
            }
            
            UnorderedLinkedList<IDivisao> entradasSaidas = new UnorderedLinkedList<>();

            for (int i = 0; i < jEntradasSaidas.size(); i++) {
                IDivisao divisao = new Divisao(jEntradasSaidas.get(i).toString());
                entradasSaidas.addToRear(divisao);
            }

            //Importar alvo
            IDivisao alvoDivisao = new Divisao(jAlvo.get("divisao").toString());
            Alvo alvo = new Alvo(alvoDivisao, jAlvo.get("tipo").toString());
            
            alvo=JsonImporter.importarAlvo(jInimigos, alvo);

            ICenario cenario = new Cenario((int) jVersao, edificio, entradasSaidas, alvo);

            missao.adicionarVersão(cenario);
            validateJSONFile(missao);

        } catch (ClassCastException e) {
            throw new InvalidDocumentException("File values are not correct!");
        }
        catch(NullPointerException e){
            throw new InvalidDocumentException("File fields are missing!");
        }
        catch(IndexOutOfBoundsException e){
            throw new InvalidDocumentException("File connections are missing!");
        }
        
        return missao;
    }

    /**
     * Validate imported JSON file.
     *
     * @return true if document is correct.
     * @return false if document does not follow base structure.
     */
    private static boolean validateJSONFile(IMissao missao) throws InvalidDocumentException, 
            InvalidOperationException, VersionAlreadyExistException, NullElementValueException {
        if (missao.getNumeroVersoes() == 0) {
            throw new InvalidDocumentException("There is none map in the document!");
        }
        
        Iterator<ICenario> iterador = missao.getListVersoes().iterator();
        
        ICenario cenario = iterador.next();

        if ( cenario.getEdificio().size() < 1
                || cenario.getAlvo().getDivisao() == null
                || cenario.getAlvo().getTipo() == null
                || cenario.getEntradasSaidas() == null) {
            throw new InvalidDocumentException("There is something wrong in the document!");
        }
        
        if(!cenario.getEdificio().isConnected()){
            throw new InvalidDocumentException("The building has divisions not connected!");
        }

        Iterator<IDivisao> iterator = cenario.getEntradasSaidas();
        
        if(cenario.getNumeroEntradasSaidas()<1){
            throw new InvalidDocumentException("The building does not have entries or exits!");
        }

        while (iterator.hasNext()) {
            try {
                cenario.getEdificio().getVertex(iterator.next());
            } catch (NullElementValueException | ElementNotFoundException ex) {
                throw new InvalidDocumentException("The entries and exits do not exist in the building!");
            }
        }
        
        try {
            cenario.getEdificio().getVertex(cenario.getAlvo().getDivisao());
        } catch (NullElementValueException | ElementNotFoundException ex) {
            
            throw new InvalidDocumentException("The division of the target does not exist in the building!");
        }

        return true;
    }
    
    /**
     * Inserir inimigos na divisão se existirem.
     * @param jInimigos
     * @param divisao
     * @return Divisao
     * @throws InvalidDocumentException
     * @throws NullElementValueException 
     */
    private static IDivisao importarDivisao(JSONArray jInimigos,IDivisao divisao) throws InvalidDocumentException, NullElementValueException{
                if(jInimigos.isEmpty()){
                    throw new InvalidDocumentException("There are no enemies in this building");
                }
                
                for (int j = 0; j < jInimigos.size(); j++) {
                    JSONObject jInimigo = (JSONObject) jInimigos.get(j);

                    IDivisao divisaoInimigo = new Divisao(jInimigo.get("divisao").toString());

                    if (divisao.equals(divisaoInimigo)) {
                        Inimigo inimigo = new Inimigo(jInimigo.get("nome").toString(),
                                (int) ((long) jInimigo.get("poder")));
                        
                        if(((long) jInimigo.get("poder"))<1){
                            throw new InvalidDocumentException("The enemy power must be greater than 0!");
                        }
                        
                        int dano = divisao.getDano() + (int) ((long) jInimigo.get("poder"));
                        divisao.setDano(dano);
                        divisao.adicionarInimigo(inimigo);
                    }
                }
                return divisao;
    }
    
    /**
     * Importar alvo do ficheiro Json.
     * @param jInimigos
     * @param alvo
     * @return Alvo
     * @throws NullElementValueException 
     */
    private static Alvo importarAlvo(JSONArray jInimigos,Alvo alvo) throws NullElementValueException{
        for (int j = 0; j < jInimigos.size(); j++) {
                JSONObject jInimigo = (JSONObject) jInimigos.get(j);

                IDivisao divisaoInimigo = new Divisao(jInimigo.get("divisao").toString());

                if (alvo.getDivisao().equals(divisaoInimigo)) {
                    Inimigo inimigo = new Inimigo(jInimigo.get("nome").toString(),
                            (int) ((long) jInimigo.get("poder")));

                    int dano = alvo.getDivisao().getDano() + (int) ((long) jInimigo.get("poder"));
                    alvo.getDivisao().setDano(dano);
                    alvo.getDivisao().adicionarInimigo(inimigo);
                }
            }
        return alvo;
    }
    
}
