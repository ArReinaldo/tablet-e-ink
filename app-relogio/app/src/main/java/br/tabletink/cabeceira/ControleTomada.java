package br.tabletink.cabeceira;

/**
 * Ponto de encaixe para uma tomada inteligente. Desde o root, o limite de carga (50/85) é feito
 * pelo serviço do Magisk service.d/limite-carga.sh, então por enquanto isto não faz nada.
 */
final class ControleTomada {

    private ControleTomada() {}

    /** Chamado a cada mudança de bateria. */
    static void aoMudarBateria(int nivel, boolean carregando) {
        // TODO: quando a tomada existir, ligar abaixo de X% e desligar acima de Y%.
    }
}
