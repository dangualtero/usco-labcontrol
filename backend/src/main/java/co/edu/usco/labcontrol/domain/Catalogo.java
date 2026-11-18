package co.edu.usco.labcontrol.domain;

import jakarta.persistence.*;

/** Valor de una lista configurable (categorías de equipos, tipos de solicitud, fuentes de financiación…). */
@Entity
@Table(name = "catalogos")
public class Catalogo {
    public enum Tipo {
        CATEGORIA_EQUIPO("Categorías de equipos e insumos"), ESTADO_FISICO("Estados físicos de los bienes"),
        TIPO_SOLICITUD("Tipos de solicitud"), TIPO_DOCUMENTO("Tipos de documento del laboratorio"),
        FUENTE_FINANCIACION("Fuentes de financiación"), MODALIDAD_CONTRATACION("Modalidades de contratación");
        private final String texto;
        Tipo(String texto) { this.texto = texto; }
        public String texto() { return texto; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;
    private String valor;
    private int orden;
    private boolean activo = true;

    protected Catalogo() {}

    public Catalogo(Tipo tipo, String valor, int orden) { this.tipo = tipo; this.valor = valor; this.orden = orden; }

    public Long getId() { return id; }
    public Tipo getTipo() { return tipo; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
