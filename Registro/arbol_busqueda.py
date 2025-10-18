class Nodo:
    def __init__(self, DNI, apellido, nombre, estado):
        self.dni = DNI
        self.apellido = apellido
        self.nombre = nombre
        self.estado = estado
        self.izq = None
        self.der = None

class ArbolBinarioBusqueda:
    def __init__(self):
        self.raiz = None

    def insertar(self, nodo):
        self.raiz = self._insertar_rec(self.raiz, nodo)

    def _insertar_rec(self, actual, nodo):
        if actual is None:
            return nodo
        if nodo.dni < actual.dni:
            actual.izq = self._insertar_rec(actual.izq, nodo)
        elif nodo.dni > actual.dni:
            actual.der = self._insertar_rec(actual.der, nodo)
        return actual

    def buscar(self, dni):
        return self._buscar_rec(self.raiz, dni)

    def _buscar_rec(self, actual, DNI):
        if actual is None:
            return None
        if DNI == actual.dni:
            return actual
        if DNI < actual.dni:
            return self._buscar_rec(actual.izq, DNI)
        return self._buscar_rec(actual.der, DNI)

    def actualizar_estado_civil(self, lista_matrimonios):
        for dni_femenino, dni_masculino, fecha in lista_matrimonios:
            nodo = self.buscar(dni_masculino)
            if nodo:
                nodo.estado = "Casado"

def PrintTXT(abb):
    print("Cedula     | Nombre   | Estado")
    print("------------------------------")
    def recorrer(nodo):
        if nodo:
            recorrer(nodo.izq)
            print(f"{str(nodo.dni).ljust(10)}| {nodo.nombre.ljust(8)}| {nodo.estado}")
            recorrer(nodo.der)
    recorrer(abb.raiz)

if __name__ == "__main__":
    abb = ArbolBinarioBusqueda()
    registros = [
    (24876443, 'Viauche',  'Fabián',  'Soltero'),
    (14242368, 'Belo',     'Gonzalo', 'Casado'),
    (27104856, 'Aguirre',  'Andrés',  'Casado'),
    (21789423, 'Álvarez',  'Pablo',   'Soltero'),
    (17678294, 'Bazán',    'Rodrigo', 'Soltero'),
    (13637531, 'Casano',   'Germán',  'Casado'),
    (14619239, 'Pinto',    'Lucas',   'Casado'),
    (30987323, 'Taguada',  'Carlos',  'Soltero'),
    (12578434, 'Borges',   'Juan',    'Soltero')
]
    for dni, ape, nom, est in registros:
        abb.insertar(Nodo(dni, ape, nom, est))

    print("┊ANTES:")
    PrintTXT(abb)

    matrimonios = [
    (12985093, 14242368, '14/12/1984'),
    (26178051, 27104856, '15/01/1974'),
    (18768418, 13637531, '05/08/1960'),
    (17455294, 14619239, '21/12/1974')
]
    abb.actualizar_estado_civil(matrimonios)

    print("\n┊DESPUÉS:")
    PrintTXT(abb)
