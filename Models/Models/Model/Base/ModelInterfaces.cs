using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Model.Base
{
    public interface IUpsertable
    {
        string Pk { get; }
        bool IsNew { get; set; }
        bool IsPending { get; set; }
        DateTime ModifiedDate { get; set; }
    }
}
